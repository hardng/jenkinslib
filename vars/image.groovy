// utils/image.groovy

def buildImage() {
  def module_list = params.MODULES.split(',')
  def app_module = readJSON text: env.APP_MODULE

  def image_tag = env.CURRENT_COMMIT_ID
  for (mod in module_list) {
    def subpath = app_module[mod]?.toString() ?: ''
    def path = "${env.ROOT_WORKSPACE}/${env.MAIN_PROJECT}/${subpath}"
    def image_addr = "${env.DOCKER_REGISTRY}/${env.JOB_PREFIX}-${mod}:${image_tag}"

    // 根据不同编译环境的上下文执行
    configFileProvider([configFile(fileId: "${env.REGISTRY_CREDNTIAL}", targetLocation: "${env.ROOT_WORKSPACE}/${env.MAIN_PROJECT}/.docker/config.json")]) {
      withEnv(["DOCKER_CONFIG=${env.ROOT_WORKSPACE}/${env.MAIN_PROJECT}/.docker"]) {
        try {
          dir(path) {
            runBuildImage(image_addr.toString())
          }
          echo "${vars.green}✅ 成功构建并推送镜像: ${image_addr}${vars.reset}"
        } catch (Exception e) {
          /* groovylint-disable-next-line UnnecessaryGetter */
          echo "错误：无法为模块 ${mod} 构建镜像 ${image_addr}，错误信息：${e}"
          error '❌ 镜像构建失败，请检查构建环境或 Dockerfile 配置'
        }
      }
    }
  }
}

def runBuildImage(image_addr) {
  String baseImage = env.BASE_IMAGE.toString()
  
  if (!fileExists('Dockerfile')) {
    error "❌ Dockerfile 不存在"
  }

  def buildCommand = """
    sed -i 's@mldockze/openjdk:17.0.10@${baseImage}@g' Dockerfile
    if command -v buildctl >/dev/null 2>&1; then
      buildctl build \\
        --frontend dockerfile.v0 \\
        --local context=. \\
        --local dockerfile=. \\
        --output type=image,name=${image_addr},push=true
    elif command -v docker >/dev/null 2>&1; then
      docker build -t ${image_addr} . --no-cache && docker push ${image_addr}
    else
      echo "❌ Buildkit 和 Docker 工具不可用"
      exit 1
    fi
  """.stripIndent().trim()

  try {
    sh """
      set -e
      ${buildCommand}
    """
  } catch (err) {
    error "❌ 构建失败，镜像: ${image_addr} ${err}"
  }
}
return this