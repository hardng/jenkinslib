// vars/common.groovy

def call() {
  return [
    withAgentWorkspace: { script, Closure body ->
      def originalRoot = script.env.ROOT_WORKSPACE
      def currentDir = script.pwd()
      script.echo "originalRoot: ${originalRoot}"
      script.echo "currentDir: ${currentDir}"
      script.env.ROOT_WORKSPACE = currentDir
      try {
        body.call()
      } finally {
        script.env.ROOT_WORKSPACE = originalRoot
      }
    },

    ex: { String paramName, def paramValue ->
      def cleanedValue = paramValue?.toString()?.trim()
      if (cleanedValue == null || cleanedValue == '') {
        currentBuild.result = 'ABORTED'
        error("参数 ${paramName} 无效：不能为空")
      }
      if (!cleanedValue.matches(/[a-zA-Z0-9_-]+/)) {
        currentBuild.result = 'ABORTED'
        error("参数 ${paramName} 无效")
      }
    }
  ]
}
