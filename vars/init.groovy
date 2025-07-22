import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def generateDynamicProperties(config) {
    return config.parameters.collect { param ->
        def resolveChoices = {
            if (param.choices instanceof Map) {
                env.APP_MODULE = JsonOutput.toJson(param.choices)
                return param.choices.keySet() as List
            } else if (param.choices instanceof List) {
                return param.choices
            } else {
                error "Unsupported choices format for parameter ${param.name}: ${param.choices.getClass()}"
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
                error "Unsupported parameter type: ${param.type}"
        }
    }
}

return this