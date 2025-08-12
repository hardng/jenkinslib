package io.jenkins.common


class CommonTools implements Serializable {

  private transient script

  private CommonTools(script) {
    this.script = script
  }

  static CommonTools getInstance(script) {
    return new CommonTools(script)
  }

  def withAgentWorkspace(script, Closure body) {
    def originalRoot = script.env.ROOT_WORKSPACE
    def currentDir = script.pwd()

    script.env.ROOT_WORKSPACE = currentDir
    try {
      body.call()
    } finally {
      script.env.ROOT_WORKSPACE = originalRoot
    }
  }

  def ex(String paramName, def paramValue) {
    def cleanedValue = paramValue?.toString()?.trim()

    // 校验字符串参数：不能为空或仅空格
    if (cleanedValue == null || cleanedValue == '') {
      script.currentBuild.result = 'ABORTED'
      script.error("参数 ${paramName} 无效：不能为空")
    }

    // 可选：添加其他格式校验（例如只允许字母、数字、下划线、连字符）
    if (!cleanedValue.matches(/[a-zA-Z0-9_-]+/)) {
      script.currentBuild.result = 'ABORTED'
      script.error("参数 ${paramName} 无效")
    }
  }
}