package io.jenkins.common
import groovy.json.JsonSlurper
import java.io.StringWriter
import java.io.PrintWriter

class CommonTools implements Serializable {
    private transient script
    private static CommonTools instance
    
    private CommonTools(script) {
      this.script = script
    }
    
    static CommonTools getInstance(script) {
      if (instance == null) {
        instance = new CommonTools(script)
      }
      return instance
    }
    
    private boolean isValidJson(String text) {
      try {
        script.readJSON(text: text)
        return true
      } catch (Exception e) {
        return false
      }
    }
    
    def withAgentWorkspace(Closure body) {
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
      // 空值校验
      if (!cleanedValue) {
        script.currentBuild.result = 'ABORTED'
        script.error("参数 ${paramName} 无效：不能为空")
      }
      // 支持多选：按逗号分割并清除首尾空格
      def values = cleanedValue.split(',').collect { it.trim() }
      // 校验每个子值
      for (def v : values) {
        if (!v.matches(/^[a-zA-Z0-9_.-]+$/)) {
          script.currentBuild.result = 'ABORTED'
          script.error("参数 ${paramName} 中的值 '${v}' 无效，只允许字母、数字、下划线、连字符、点")
        }
      }
    }
    
    def checkPreviousBuildAndSetEnv() {
      def prevBuild = script.currentBuild.rawBuild.getPreviousBuild()
      if (!prevBuild) {
        script.echo "⚠️ 没有找到上一次构建，跳过元数据解析"
        return
      }
      
      def prevDesc = prevBuild.getDescription() ?: ""
      if (!prevDesc || prevDesc.trim().isEmpty()) {
        script.echo "⚠️ 上一次构建描述为空，跳过元数据解析"
        return
      }
      
      def meta
      try {
        meta = new JsonSlurper().parseText(prevDesc)
      } catch (Exception e) {
        def sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        script.echo "📌 堆栈详情:\n${sw.toString()}"
        script.echo "📌 原始描述内容: ${prevDesc}"
        return
      }
      
      // 保存字段到环境变量，方便后续引用
      // 使用安全的属性访问方式
      script.env.PREVIOUS_COMMIT_ID = meta?.commit ?: ''
      script.env.PREVIOUS_MODULES = meta?.modules ?: ''
      script.env.PREVIOUS_BUILD_SUCCESS = (meta?.success == true).toString()
      script.env.PREVIOUS_IMAGE_UPLOADED = (meta?.imageUploaded == true).toString()
      script.env.EXEC_RESULT = meta?.exec ?: 'false'
      
      def currentModules = (script.params.MODULES ?: '').trim()
      script.env.CURRENT_MODULES = currentModules
      
      def previousModules = meta?.modules ?: []
      script.env.SAME_MODULES = (currentModules == previousModules).toString()
  }
  
  def shouldSkipStage(stageName = null) {
    def fallback = script.env.USED_FALLBACK_BRANCH == 'true'
    if (!stageName) {
      return fallback
    }
    
    switch (stageName) {
      case 'compile':
          return fallback ||
              (script.env.CURRENT_COMMIT_ID == script.env.PREVIOUS_COMMIT_ID) &&
              (script.env.SAME_MODULES?.toBoolean() == true) &&
              (script.env.PREVIOUS_BUILD_SUCCESS?.toBoolean() == true) &&
              (script.env.PREVIOUS_IMAGE_UPLOADED?.toBoolean() == true)
      case 'deploy':
        return fallback || script.env.SKIP_DEPLOY_STAGE?.toBoolean() == true
      default:
        return fallback
    }
  }
}