import org.yaml.snakeyaml.Yaml

def job_folder = 'products'

def repos = new URL("https://raw.githubusercontent.com/lsst/lsstsw/master/etc/repos.yaml")

def y = new Yaml()
def doc = y.load(repos.newReader())

def locations = [:]
doc.each { k, v ->
  def url
  if (v instanceof LinkedHashMap) {
    url = v['url']
  } else {
    url = v
  }
  assert url

  // convert GH url into project slug
  def m = url =~ /^http[s]:\/\/github.com\/([^.\/]*\/[^.\/]*)(\.git)?$/
  assert m
  def slug = m[0][1]
  assert slug

  locations[k] = slug
}

folder(job_folder) {
  displayName('EUPS Products')
}

locations.each { name,slug ->
  println "${name} : ${slug}"
  stack_job(job_folder, name, slug)
}

def stack_job(String folder, String name, String slug) {
  job("${folder}/${name}") {
    scm {
      git {
        remote {
          github(slug)
          //refspec('+refs/pull/*:refs/remotes/origin/pr/*')
        }
        branch('*/master')
        extensions {
          cloneOptions {
            shallow(true)
          }
        }
      }
    }

    parameters {
      stringParam('BRANCH', null, "Whitespace delimited list of 'refs' to attempt to build.  Priority is highest -> lowest from left to right.  'master' is implicitly appended to the right side of the list, if not specified.")
    }

    properties {
      rebuild {
        autoRebuild()
      }
    }
    concurrentBuild()
    label('jenkins-master')
    keepDependencies()

    triggers {
      githubPush()
      pullRequest {
        cron('H/5 * * * *')
        useGitHubHooks()
        permitAll()
        // configure credential to use for GH API
        configure { project ->
          project / 'triggers' / 'org.jenkinsci.plugins.ghprb.GhprbTrigger' {
            gitHubAuthId 'github-api-token-jhoblitt'
          }
        }
      }
    }

    wrappers {
      // 'The ghprbSourceBranch' variable is defined when the build was triggered
      // by a GH PR.  If set, extract it for later use as the 'BRANCH' parameter
      // to the 'stack-os-matrix'.  Otherwise, pass on the 'BRANCH' parameter
      // this build was invoked with (it may be blank).
      configure { project ->
        project / 'buildWrappers' / 'EnvInjectBuildWrapper' / 'info' {
          // yes, groovy heredocs are this lame...
          groovyScriptContent """
  if (binding.variables.containsKey('ghprbSourceBranch')) {
    return [BUILD_BRANCH: ghprbSourceBranch]
  } else {
    return [BUILD_BRANCH: BRANCH]
  }
  """
        }
      }
    }

    steps {
      downstreamParameterized {
        trigger('stack-os-matrix') {
          block {
            buildStepFailure('FAILURE')
            failure('FAILURE')
          }
          parameters {
            //currentBuild()
            predefinedProps([
              PRODUCT: name,
              BRANCH: '$BUILD_BRANCH'
            ])
            booleanParam('SKIP_DEMO', true)
          }
        }
      }
    }

    publishers {
      // must be defined even to use the global defaults
      hipChat {}
    }
  }
}