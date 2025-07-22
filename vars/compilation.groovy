def build(hook_funcs) {
  script {
    runBuild(hook_funcs)
  }
}

def runBuild(hook_funcs) {
  script {
    def programming = env.PROGRAMMING
    def buildCommand = env.BUILD_COMMAND
    def setting_config = readJSON text: env.SELECTED_MODULE_CONFIG_JSON

    if (!buildCommand) {
      error "没有传入编译命令: ${programming}"
    }

    // 前置钩子
    if (setting_config.build_pre) {
      echo "====================================== ƒ 开始执行前置钩子 ======================================"
      setting_config.build_pre.each { hook ->
        echo """
        | 钩子函数: ${hook.function}
        | 参数: ${hook.args.inspect()}
        |---------------------------------------
        """.stripMargin()
        try {
          hook_funcs."${hook.function}"(hook.args ?: [:])
        } catch (Exception e) {
          error "执行前置钩子 ${hook.function} 失败: ${e.message}"
        }
      }
      echo "====================================== ƒ 前置钩子执行完成 ======================================\n"
    }

    echo "====================================== ⚒️ 开始执行编译 ======================================"

    try {
      switch (programming) {
        case 'java':
          echo "编译命令: ${buildCommand}"
          configFileProvider([configFile(fileId: "${env.MAVEN_SETTINGS}", targetLocation: "settings.xml")]) {
            sh """
              set -e
              ${buildCommand}
            """
          }
          break
        default:
          sh """
            set -e
            ${buildCommand}
          """
      }
      echo "====================================== ⚒️ 编译执行完成 ======================================"
    } catch (Exception e) {
      echo "编译失败: ${e.message}"
      throw e
    }

    // 后置钩子
    if (setting_config.build_post) {
      echo "====================================== ƒ 开始执行后置钩子 ======================================"
      setting_config.build_post.each { hook ->
        echo """
        | 钩子函数: ${hook.function}
        | 参数: ${hook.args.inspect()}
        |---------------------------------------
        """.stripMargin()
        try {
          hook_funcs."${hook.function}"(hook.args ?: [:])
        } catch (Exception e) {
          error "执行后置钩子 ${hook.function} 失败: ${e.message}"
        }
      }
      echo "====================================== ƒ 后置钩子执行完成 ======================================"
    }
  }
}

return this
