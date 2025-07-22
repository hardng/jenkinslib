def pullCode(String git_repo, String git_credentials, String branch) {
  def selectedBranch = branch
  def commitId = null
  // 判断是否是构建 #1，用于获取参数，而不是真正构建
  env.IS_FIRST_BUILD = (currentBuild.number == 1) ? 'true' : 'false'
  // 备用分支列表
  def fallbackBranches = env.FALLBACK_BRANCHES ? env.FALLBACK_BRANCHES.split(',').collect { it.trim() } : ['master', 'test']

  // 尝试拉取分支
  try {
    checkout([
      $class: 'GitSCM',
      branches: [[name: "*/${selectedBranch}"]],
      userRemoteConfigs: [[credentialsId: "${git_credentials}", url: "${git_repo}"]],
      extensions: [[$class: 'CloneOption', timeout: 5]]
    ])
    commitId = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    if (env.IS_FIRST_BUILD == 'true') {
      env.USED_FALLBACK_BRANCH = 'true'
      echo "第一次执行（构建 #${currentBuild.number}），成功拉取分支 ${selectedBranch}，根据要求终止流水线"
    }
    return commitId
  } catch (Exception e) {
    echo "拉取分支 ${selectedBranch} 失败: ${e.message}"
  }

  // 如果main分支不存在，拉取失败，尝试备用分支获取列表
  // 因为第一次使用默认分支为main，会走到这一步
  for (fallbackBranch in fallbackBranches) {
    if (fallbackBranch != selectedBranch) {
      try {
        echo "尝试备用分支: ${fallbackBranch}"
        checkout([
          $class: 'GitSCM',
          branches: [[name: "*/${fallbackBranch}"]],
          userRemoteConfigs: [[credentialsId: "${git_credentials}", url: "${git_repo}"]],
          extensions: [[$class: 'CloneOption', timeout: 5]]
        ])
        commitId = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        echo "成功通过备用分支 ${fallbackBranch} 获取了参数列表，根据要求终止流水线"
        env.USED_FALLBACK_BRANCH = 'true'
        return commitId
      } catch (Exception ex) {
        echo "拉取备用分支 ${fallbackBranch} 失败: ${ex.message}"
      }
    }
  }

  // 如果所有分支都失败，抛出错误
  error "无法拉取任何分支（尝试了 ${selectedBranch} 和 ${fallbackBranches}）。请检查仓库 ${git_repo} 和凭据 ${credentialId}。"
}

return this