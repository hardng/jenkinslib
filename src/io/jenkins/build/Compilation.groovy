// utils/compilation.groovy
package io.jenkins.build
import io.jenkins.common.Colors
import groovy.json.JsonOutput

class Compilation implements Serializable {
  private transient script
  private static Compilation instance

  private Compilation(script) {
    this.script = script
  }

  static Compilation getInstance(script) {
    if (instance == null) {
      instance = new Compilation(script)
    }
    return instance
  }

  def build(hook_funcs) {
    runBuild(hook_funcs)
  }

  def runBuild(hook_funcs) {
    script.script {
      def programming = script.env.PROGRAMMING
      def buildCommand = script.env.BUILD_COMMAND
      def setting_config = script.readJSON text: script.env.SELECTED_MODULE_CONFIG_JSON

      if (!buildCommand) {
        script.error "没有传入编译命令: ${programming}"
      }

      // 前置钩子
      if (setting_config.build_pre) {
        script.echo "${Colors.BRIGHT_CYAN}====================================== ƒ 开始执行前置钩子 ======================================${Colors.RESET}"
        setting_config.build_pre.each { hook ->
          script.echo """
          | 钩子函数: ${hook.function}
          | 参数: ${JsonOutput.prettyPrint(JsonOutput.toJson(hook.args ?: [:]))}
          |---------------------------------------
          """.stripMargin()
          try {
            hook_funcs."${hook.function}"(hook.args ?: [:])
          } catch (Exception e) {
            script.error "执行前置钩子 ${hook.function} 失败: ${e.message}"
            script.env.PREVIOUS_BUILD_SUCCESS = 'false'
          }
        }
        script.echo "${Colors.BRIGHT_CYAN}====================================== ƒ 前置钩子执行完成 ======================================${Colors.RESET}"
      }

        script.echo "${Colors.BRIGHT_CYAN}======================================= ⚒️ 开始执行编译 ========================================${Colors.RESET}"

      try {
        script.echo "编译命令: ${buildCommand}"
        switch (programming) {
          case 'java':
            script.configFileProvider([script.configFile(fileId: "${script.env.MAVEN_SETTINGS}", targetLocation: "settings.xml")]) {
              script.sh """
                set -e
                ${buildCommand}
              """
            }
            break
          case 'rust':
            script.withCredentials([usernamePassword(credentialsId: "${script.env.GIT_CREDNTIAL}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
              script.sh """
                set -e
                mkdir -p ~/.cargo
                echo "[net]\ngit-fetch-with-cli = true" > ~/.cargo/config.toml
                cat > ~/.netrc <<-EOF
machine git.nexus58.com
login $GIT_USER
password $GIT_PASS
EOF
                chmod 600 ~/.netrc
                ${buildCommand}
              """
            }
            break
          default:
            script.sh """
              set -e
              ${buildCommand}
            """
        }
        script.echo "${Colors.BRIGHT_CYAN}======================================= ⚒️ 编译执行完成 ========================================${Colors.RESET}"
      } catch (Exception e) {
        script.echo "编译失败: ${e.message}"
        script.env.PREVIOUS_BUILD_SUCCESS = 'false'
        throw e
      }

      // 后置钩子
      if (setting_config.build_post) {
        script.echo "${Colors.BRIGHT_CYAN}====================================== ƒ 开始执行后置钩子 ======================================${Colors.RESET}"
        
        setting_config.build_post.each { hook ->
          script.echo """
          | 钩子函数: ${hook.function}
          | 参数: ${JsonOutput.prettyPrint(JsonOutput.toJson(hook.args ?: [:]))}
          |---------------------------------------
          """.stripMargin()
          try {
            hook_funcs."${hook.function}"(hook.args ?: [:])
          } catch (Exception e) {
            script.env.PREVIOUS_BUILD_SUCCESS = 'false'
            script.error "执行后置钩子 ${hook.function} 失败: ${e.message}"
          }
        }
        script.echo "${Colors.BRIGHT_CYAN}====================================== ƒ 后置钩子执行完成 ======================================${Colors.RESET}"
      }
      script.env.PREVIOUS_BUILD_SUCCESS = script.env.PREVIOUS_BUILD_SUCCESS == 'true' ? 'true' : 'true'
    }
  }

}