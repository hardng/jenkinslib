// utils/common.groovy

def withAgentWorkspace(script, Closure body) {
  def originalRoot = script.env.ROOT_WORKSPACE
  def currentDir = script.pwd()
  echo "originalRoot: ${originalRoot}"
  echo "currentDir: ${currentDir}"
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
    currentBuild.result = 'ABORTED'
    error("参数 ${paramName} 无效：不能为空")
  }

  // 可选：添加其他格式校验（例如只允许字母、数字、下划线、连字符）
  if (!cleanedValue.matches(/[a-zA-Z0-9_-]+/)) {
    currentBuild.result = 'ABORTED'
    error("参数 ${paramName} 无效")
  }
}

return this