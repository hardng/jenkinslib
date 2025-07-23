// vars/build.groovy
def call() {
  return [
    build: { script, hook_funcs ->
      _runBuild(hook_funcs)
    }
  ]
}

private def _runBuild(script, hook_funcs) {
  def programming = script.env.PROGRAMMING
  def buildCommand = script.env.BUILD_COMMAND
  def setting_config = readJSON text: script.env.SELECTED_MODULE_CONFIG_JSON

  if (!buildCommand) {
    error "❌ 没有传入编译命令: ${programming}"
  }

  // ===== 前置钩子 =====
  if (setting_config.build_pre) {
    echo "====================================== ⚙️  开始执行前置钩子 ======================================"
    setting_config.build_pre.each { hook ->
      echo """
      | 🪝 前置钩子: ${hook.function}
      | 参数: ${hook.args.inspect()}
      |---------------------------------------
      """.stripMargin()
      try {
        hook_funcs."${hook.function}"(hook.args ?: [:])
      } catch (Exception e) {
        error "❌ 执行前置钩子 ${hook.function} 失败: ${e.message}"
      }
    }
    echo "====================================== ✅ 前置钩子执行完成 ======================================\n"
  }

  // ===== 编译阶段 =====
  echo "====================================== ⚒️  开始执行编译命令 ======================================"
  try {
    echo "🧾 编译语言: ${programming}"
    echo "📜 编译命令: ${buildCommand}"

    switch (programming) {
      case 'java':
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

    echo "====================================== ✅ 编译执行完成 ======================================"
  } catch (Exception e) {
    echo "❌ 编译失败: ${e.message}"
    throw e
  }

  // ===== 后置钩子 =====
  if (setting_config.build_post) {
    echo "====================================== ⚙️  开始执行后置钩子 ======================================"
    setting_config.build_post.each { hook ->
      echo """
      | 🪝 后置钩子: ${hook.function}
      | 参数: ${hook.args.inspect()}
      |---------------------------------------
      """.stripMargin()
      try {
        hook_funcs."${hook.function}"(hook.args ?: [:])
      } catch (Exception e) {
        error "❌ 执行后置钩子 ${hook.function} 失败: ${e.message}"
      }
    }
    echo "====================================== ✅ 后置钩子执行完成 ======================================"
  }
}
