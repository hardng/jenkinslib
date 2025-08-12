// src/io/jenkins/common/Init.groovy
package io.jenkins.common

import groovy.json.JsonOutput

class Init implements Serializable {
  private transient script

  private Init(script) {
    this.script = script
  }

  static Init getInstance(script) {
    return new Init(script)
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