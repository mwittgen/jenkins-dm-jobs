def config = null

node('jenkins-master') {
  dir('jenkins-dm-jobs') {
    checkout([
      $class: 'GitSCM',
      branches: scm.getBranches(),
      userRemoteConfigs: scm.getUserRemoteConfigs(),
      changelog: false,
      poll: false
    ])
    notify = load 'pipelines/lib/notify.groovy'
    util = load 'pipelines/lib/util.groovy'
    config = util.readYamlFile 'etc/science_pipelines/build_matrix.yaml'
  }
}

notify.wrap {
  util.requireParams([
    'EUPS_TAG',
    'PRODUCT',
    'PUBLISH',
    'RUN_DEMO',
    'RUN_SCONS_CHECK',
    'SMOKE',
  ])

  String eupsTag        = params.EUPS_TAG
  String product        = params.PRODUCT
  Boolean publish       = params.PUBLISH
  Boolean runDemo       = params.RUN_DEMO
  Boolean runSconsCheck = params.RUN_SCONS_CHECK
  Boolean smoke         = params.SMOKE

  def retries = 3

  def opt = [
    SMOKE: smoke,
    RUN_DEMO: runDemo,
    RUN_SCONS_CHECK: runSconsCheck,
    PUBLISH: publish,
  ]

  timeout(time: 30, unit: 'HOURS') {
    stage('build eups tarballs') {
      util.buildTarballMatrix(config, product, eupsTag, opt)
    }
  }
} // notify.wrap
