// src/io/jenkins/common/Stages.groovy
package io.jenkins.common
import io.jenkins.common.Colors
/**
 * Stages
 * 
 * 用法：
 *   import io.jenkins.common.Stages.*
 */
class Stages implements Serializable {
  private transient script

  private Stages(script) {
    this.script = script
  }

  static Stages getInstance(script) {
    return new Stages(script)
  }
  def pullCode(String git_repo, String git_credentials, String branch) {
    def selectedBranch = branch
    def commitId = null
    // 判断是否是构建 #1，用于获取参数，而不是真正构建
    script.env.IS_FIRST_BUILD = (script.currentBuild.number == 1) ? 'true' : 'false'
    // 备用分支列表
    def fallbackBranches = script.env.FALLBACK_BRANCHES ? script.env.FALLBACK_BRANCHES.split(',').collect { it.trim() } : ['master', 'test']

    // 尝试拉取分支
    try {
      script.checkout([
        $class: 'GitSCM',
        branches: [[name: "*/${selectedBranch}"]],
        userRemoteConfigs: [[credentialsId: "${git_credentials}", url: "${git_repo}"]],
        extensions: [[$class: 'CloneOption', timeout: 5], [$class: 'WipeWorkspace']]
      ])
      commitId = script.sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
      if (script.env.IS_FIRST_BUILD == 'true') {
        script.env.USED_FALLBACK_BRANCH = 'true'
        script.echo "${Colors.BG_CYAN}第一次执行（构建 #${script.currentBuild.number}），成功拉取分支 ${selectedBranch}，根据要求终止流水线${Colors.RESET}"
        script.currentBuild.result = 'ABORTED'
      }
      return commitId
    } catch (Exception e) {
      script.echo "${Colors.RED}拉取分支 ${selectedBranch} 失败: ${e}${Colors.RESET}"
    }

    // 如果main分支不存在，拉取失败，尝试备用分支获取列表
    // 因为第一次使用默认分支为main，会走到这一步
    for (fallbackBranch in fallbackBranches) {
      fallbackBranch = fallbackBranch.replaceAll('"', '')
      if (fallbackBranch != selectedBranch) {
        try {
          script.echo "${Colors.YELLOW}尝试备用分支: ${fallbackBranch}${Colors.RESET}"
          checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${fallbackBranch}"]],
            userRemoteConfigs: [[credentialsId: "${git_credentials}", url: "${git_repo}"]],
            extensions: [[$class: 'CloneOption', timeout: 5], [$class: 'WipeWorkspace']]
          ])
          commitId = script.sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
          script.echo "${Colors.GREEN}成功通过备用分支 ${fallbackBranch} 获取了参数列表，根据要求终止流水线${Colors.RESET}"
          script.env.USED_FALLBACK_BRANCH = 'true'
          return commitId
        } catch (Exception ex) {
          script.echo "${Colors.RED}拉取备用分支 ${fallbackBranch} 失败: ${ex.message}${Colors.RESET}"
        }
      }
    }

    // 如果所有分支都失败，抛出错误
    script.error "无法拉取任何分支（尝试了 ${selectedBranch} 和 ${fallbackBranches}）。请检查仓库 ${git_repo} 和凭据 ${git_credentials}。"
  }
}