// src/io/jenkins/common/Init.groovy
package io.jenkins.common

import groovy.json.JsonOutput
import io.jenkins.common.Colors
import io.jenkins.common.Stages
import io.jenkins.common.Init
import io.jenkins.common.CommonTools

class Init implements Serializable {
  private transient script

  private Init(script) {
    this.script = script
  }

  static Init getInstance(script) {
    return new Init(script)
  }

  void initGlobalVariables() {
    script.git_client       = new Stages(script)
    script.common           = new CommonTools(script)
    //初始化配置
    def config = script.readJSON(file: 'modules.json')
    def jobName = script.env.JOB_NAME
    def selectedModuleConfig = config[jobName]
    def globalConfig = config["global"]

    // job 配置
    script.env.SELECTED_MODULE_CONFIG_JSON      = JsonOutput.toJson(selectedModuleConfig)
    script.env.GLOBAL_CONFIG_JSON               = JsonOutput.toJson(globalConfig)
    script.env.PROGRAMMING                      = selectedModuleConfig.programming?.toString() ?: ""
    script.env.DOCKER_REGISTRY                  = selectedModuleConfig.docker_registry_url_prefix?.toString() ?: ""
    script.env.JOB_PREFIX                       = selectedModuleConfig.job_prefix?.toString() ?: ""
    script.env.GIT_REPO                         = selectedModuleConfig.git?.toString() ?: ""
    script.env.BUILD_COMMAND                    = selectedModuleConfig.build_command?.toString() ?: ""
    script.env.BUILD_PLATFORM                   = selectedModuleConfig.build_platform?.toString() ?: "docker"
    script.env.PLATFORM                         = selectedModuleConfig.platform?.toString() ?: ""
    script.env.LOG_DEBUG                        = selectedModuleConfig.log_debug?.toString() ?: "false"
    script.env.SKIP_BUILD_IMG                   = selectedModuleConfig.skip_build_img?.toString() ?: "false"
    script.env.SKIP_DEPLOY_STAGE                = selectedModuleConfig.skip_deploy_stage?.toString() ?: "false"
    script.env.FORCE_BUILD                      = selectedModuleConfig.force_build?.toString() ?: "false"
    script.env.IS_PARALLEL                      = selectedModuleConfig.is_parallel?.toString() ?: "false"
    
    if (script.env.BUILD_PLATFORM?.trim() == "kubernetes" || script.env.BUILD_PLATFORM?.trim() == "docker") {
      script.env.IMAGES      = selectedModuleConfig.images?.toString() ?: ""
      script.env.INSIDE_ARGS = selectedModuleConfig.inside_args?.toString() ?: ""
    }

    if (script.env.PLATFORM?.trim() == "vm"){
      script.env.DESTNATION_DIR     = selectedModuleConfig.destination_dir?.toString() ?: ""
      script.env.DESTINATION_HOST   = selectedModuleConfig.destination_host?.toString() ?: ""
      script.env.EXEC_COMMAND       = selectedModuleConfig.exec_command?.toString() ?: ""
    }

    script.env.MAIN_PROJECT           = script.env.GIT_REPO.tokenize('/').last().replaceFirst(/\.git$/, '')
    script.env.ROOT_WORKSPACE         = script.pwd()
    script.env.USED_FALLBACK_BRANCH   = "false"
    script.env.BASE_IMAGE             = selectedModuleConfig.base_image?.toString() ?: ""
    script.env.SHARED_MODULE          = selectedModuleConfig.shared_module?.toString() ?: "false"
    script.env.MANIFEST_PREFIX        = selectedModuleConfig.manifest_prefix ?: ""

    // 全局配置
    script.env.KUBECONFIG           = globalConfig.kubeconfig?.toString() ?: ""
    script.env.MAVEN_SETTINGS       = globalConfig.maven_settings?.toString() ?: ""
    script.env.MAVEN_CREDENTIAL     = globalConfig.maven_credential?.toString() ?: ''
    script.env.DEFAULT_BRANCH       = globalConfig.default_branch?.toString() ?: ""
    script.env.REGISTRY_CREDNTIAL   = globalConfig.registry_credential?.toString() ?: ""
    script.env.GIT_CREDNTIAL        = globalConfig.git_credential?.toString() ?: ""
    
    // 不安全方法
    //env.FALLBACK_BRANCHES = globalConfig.fallback_branches ? globalConfig.fallback_branches.join(',') : "master,test"
    
    // 安全方式
    script.env.FALLBACK_BRANCHES = globalConfig.fallback_branches
        ? globalConfig.fallback_branches.collect { it.toString() }.join(',')
        : "master,test"
    script.env.DEPLOY_CLUSTER = globalConfig.deploy_cluster

    // 初始化菜单
    def paramsList = script.init.generateDynamicProperties(selectedModuleConfig)
    def fixedParams = [
      script.gitParameter(
        branch: '',
        branchFilter: 'origin/(.*)',
        defaultValue: "${script.env.DEFAULT_BRANCH}",
        description: '请选择发布的分支名',
        name: 'selectedBranch',
        quickFilterEnabled: true,
        selectedValue: 'DEFAULT',
        sortMode: 'DESCENDING_SMART',
        tagFilter: '*',
        type: 'PT_BRANCH_TAG',
        useRepository: "${script.env.GIT_REPO}",
      )
    ]
    script.properties([
      script.parameters(fixedParams + paramsList)
    ])

    script.params.each { paramName, paramValue ->
      script.common.ex(paramName, paramValue)
    }

    // 获取上次构建信息
    script.common.checkPreviousBuildAndSetEnv()

    def env_output = """
      \u001B[36m======  🧩 JOB环境变量 ======\u001B[0m
      🔹 PROGRAMMING: ${Colors.BLUE}${script.env.PROGRAMMING}${Colors.RESET}
      🔹 GIT_REPO: ${Colors.BLUE}${script.env.GIT_REPO}${Colors.RESET}
      🔹 SHARED_MODULE : ${Colors.BLUE}${script.env.SHARED_MODULE }${Colors.RESET}
      🔹 BUILD_COMMAND: ${Colors.BLUE}${script.env.BUILD_COMMAND}${Colors.RESET}
      🔹 BUILD_PLATFORM: ${Colors.BLUE}${script.env.BUILD_PLATFORM}${Colors.RESET}
      🔹 PLATFORM: ${Colors.BLUE}${script.env.PLATFORM}${Colors.RESET}
      🔹 MAIN_PROJECT: ${Colors.BLUE}${script.env.MAIN_PROJECT}${Colors.RESET}
      🔹 ROOT_WORKSPACE: ${Colors.BLUE}${script.env.ROOT_WORKSPACE}${Colors.RESET}
    """.stripIndent()
    
    if (script.env.PROGRAMMING?.trim() == "java") {
      env_output += """
        🔹 MAVEN_CREDENTIAL: ${Colors.BLUE}${script.env.MAVEN_CREDENTIAL}${Colors.RESET}
        🔹 MAVEN_SETTINGS: ${Colors.BLUE}${script.env.MAVEN_SETTINGS}${Colors.RESET}
      """.stripIndent()
    }

    if (script.env.BUILD_PLATFORM?.trim() == "kubernetes") {
      env_output += """
        🔹 DEPLOY_CLUSTER: ${Colors.BLUE}${script.env.DEPLOY_CLUSTER}${Colors.RESET}
        🔹 DOCKER_REGISTRY: ${Colors.BLUE}${script.env.DOCKER_REGISTRY}${Colors.RESET}
      """.stripIndent()
    }

    if (script.env.PLATFORM?.trim() == "vm") {
      env_output += """
        🔹 DESTNATION_DIR: ${Colors.BLUE}${script.env.DESTNATION_DIR}${Colors.RESET}
        🔹 DESTINATION_HOST: ${Colors.BLUE}${script.env.DESTINATION_HOST}${Colors.RESET}
        🔹 EXEC_COMMAND: ${Colors.BLUE}${script.env.EXEC_COMMAND}${Colors.RESET}
      """.stripIndent()
    }

    if (script.env.BUILD_PLATFORM?.trim() == "docker" || script.env.PLATFORM?.trim() == "kubernetes") {
      def imagesMap = [:]
      def insideArgsMap = [:]

      if (script.env.IMAGES?.trim()) {
        try {
          imagesMap = script.readJSON(text: script.env.IMAGES)
        } catch (e) {}
      }

      if (script.env.INSIDE_ARGS?.trim()) {
        try {
          insideArgsMap = script.readJSON(text: script.env.INSIDE_ARGS)
        } catch (e) {}
      }

      if (imagesMap) {
        imagesMap.each { key, value ->
          env_output += " 🔸 images.${key}: ${Colors.BLUE}${value}${Colors.RESET}\n"
        }
      } else {
        env_output += " 🔸 images: ${Colors.BLUE}null${Colors.RESET}\n"
      }

      if (insideArgsMap) {
        insideArgsMap.each { key, value ->
          env_output += " 🔸 inside_args.${key}: ${Colors.BLUE}${value}${Colors.RESET}\n"
        }
      } else {
        env_output += " 🔸 inside_args: ${Colors.BLUE}null${Colors.RESET}\n"
      }
    }

    env_output = env_output.replaceAll(/\n{2,}/, "\n")
    env_output = env_output.stripIndent()
    def params_output = env_output += "\n\n\u001B[36m====== 📋 当前参数 ======\u001B[0m\n"
    script.params.each { key, value ->
      def strVal = value.toString()
      def color = strVal.equalsIgnoreCase('true') ? Colors.GREEN :
                  strVal.equalsIgnoreCase('false') ? Colors.RED :
                  Colors.BLUE
      def prefix = strVal.equalsIgnoreCase('true') ? '🟢' :
                    strVal.equalsIgnoreCase('false') ? '🔴' :
                    '🔹'
      params_output += "${prefix} ${key}: ${color}${strVal}${Colors.RESET}\n"
    }

    def previous_build_output = """
      📌 ${Colors.RED}上次构建 Commit: ${Colors.BLUE}${script.env.PREVIOUS_COMMIT_ID}${Colors.RESET}
      📌 ${Colors.RED}上次模块: ${Colors.BLUE}${script.env.PREVIOUS_MODULES}${Colors.RESET}
      📌 ${Colors.RED}当前模块: ${Colors.BLUE}${script.env.CURRENT_MODULES}${Colors.RESET}
    """.stripIndent()
    params_output += "${previous_build_output}"
    script.echo params_output.stripIndent()
  }

  def generateDynamicProperties(config) {
    return config.parameters.collect { param ->
      def resolveChoices = {
        if (param.choices instanceof Map) {
          script.env.APP_MODULE = JsonOutput.toJson(param.choices)
          return param.choices.keySet() as List
        } else if (param.choices instanceof List) {
          return param.choices
        } else {
          script.error "Unsupported choices format for parameter ${param.name}: ${param.choices.getClass()}"
        }
      }

      switch (param.type) {
        case "multi-choice":
          def choicesList = resolveChoices()
          return [
            $class: 'ChoiceParameter',
            name: param.name,
            description: param.description ?: '多选参数',
            choiceType: 'PT_CHECKBOX',
            filterable: true,
            filterLength: 1,
            randomName: "choice-parameter-${UUID.randomUUID().toString()}",
            script: [
              $class: 'GroovyScript',
              script: [
                classpath: [],
                sandbox: true,
                script: "return ${groovy.json.JsonOutput.toJson(choicesList)}"
              ],
              fallbackScript: [
                classpath: [],
                sandbox: true,
                script: "return ['加载失败']"
              ]
            ]
          ]

        case "choice":
            def choicesList = resolveChoices()
            return [
              $class: 'ChoiceParameterDefinition',
              name: param.name,
              description: param.description ?: '',
              choices: choicesList.join('\n')
            ]

        case "string":
            return [
              $class: 'StringParameterDefinition',
              name: param.name,
              description: param.description ?: '',
              defaultValue: param.default ?: ''
            ]

        case "boolean":
            return [
              $class: 'BooleanParameterDefinition',
              name: param.name,
              description: param.description ?: '',
              defaultValue: param.default ?: false
            ]

        default:
          script.error "Unsupported parameter type: ${param.type}"
      }
    }
  }
}