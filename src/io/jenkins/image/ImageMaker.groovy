// src/io/jenkins/image/ImageTools.groovy
package io.jenkins.image

import io.jenkins.common.Colors

class ImageMaker implements Serializable {
    private transient script
    private static ImageMaker instance

    private ImageMaker(script) {
      this.script = script
    }

    static ImageMaker getInstance(script) {
      if (instance == null) {
        instance = new ImageMaker(script)
      }
      return instance
    }

  def buildImage() {
    def module_list = script.params.MODULES.split(',')
    def app_module = readJSON text: script.env.APP_MODULE
    def image_tag = script.env.CURRENT_COMMIT_ID

    switch (script.env.PROGRAMMING) {
      case 'frontend':
      case 'vue':
      case 'js':
        if (script.env.SHARED_MODULE == 'true') {
          // 共享模块的镜像构建
          def first_mod = module_list[0]
          def subpath = app_module[first_mod]?.toString() ?: ''
          def path = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/${subpath}"
          def image_addr = "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}:${image_tag}"
          def projectName = "${script.env.JOB_PREFIX}"
          def dockerfileContent = """
            FROM nginx:1.22
            RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list && \
                  apt update && apt install wget && \
                  ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
                  rm -rf /var/cache/apt/*
            COPY dist/ /usr/share/nginx/html
          """.stripIndent()
          script.configFileProvider([configFile(fileId: "${script.env.REGISTRY_CREDNTIAL}", targetLocation: "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker/config.json")]) {
            script.withEnv(["DOCKER_CONFIG=${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker"]) {
              try {
                script.dir(path) {
                  script.writeFile file: 'Dockerfile', text: dockerfileContent
                  runBuildImage(image_addr.toString())
                }
                script.echo "${Colors.GREEN}✅ 成功构建并推送镜像: ${image_addr}${Colors.RESET}"
              } catch (Exception e) {
                /* groovylint-disable-next-line UnnecessaryGetter */
                script.echo "${Colors.RED}错误：无法为模块 ${mod} 构建镜像 ${image_addr}，错误信息：${e}${Colors.RESET}"
                script.error '❌ 镜像构建失败，请检查构建环境或 Dockerfile 配置'
              }
            }
          }
        } else {
          // 独立模块的镜像构建
          for (mod in module_list) {
            def subpath = app_module[mod]?.toString() ?: ''
            def path = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/${subpath}"
            def image_addr = "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}-${mod}:${image_tag}"
            def projectName = "${script.env.JOB_PREFIX}-${mod}"
            def dockerfileContent = """
              FROM nginx:1.22
              RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list && \
                    apt update && apt install wget && \
                    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
                    rm -rf /var/cache/apt/*
              COPY dist/ /usr/share/nginx/html
            """.stripIndent()

            script.configFileProvider([configFile(fileId: "${script.env.REGISTRY_CREDNTIAL}", targetLocation: "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker/config.json")]) {
              script.withEnv(["DOCKER_CONFIG=${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker"]) {
                try {
                  script.dir(path) {
                    script.writeFile file: 'Dockerfile', text: dockerfileContent
                    runBuildImage(image_addr.toString())
                  }
                  script.echo "${Colors.GREEN}✅ 成功构建并推送镜像: ${image_addr}${Colors.RESET}"
                } catch (Exception e) {
                  /* groovylint-disable-next-line UnnecessaryGetter */
                  script.echo "${Colors.RED}错误：无法为模块 ${mod} 构建镜像 ${image_addr}，错误信息：${e}${Colors.RESET}"
                  script.error '❌ 镜像构建失败，请检查构建环境或 Dockerfile 配置'
                }
              }
            }
          }
        }
        break
      case "rust":
        if (script.env.SHARED_MODULE == 'true') {
          // 共享模块的镜像构建
          def first_mod = module_list[0]
          def subpath = app_module[first_mod]?.toString() ?: ''
          def path = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/${subpath}"
          def image_addr = "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}:${image_tag}"
          def projectName = "${script.env.JOB_PREFIX}"
          def dockerfileContent = """
            FROM hub.rancher8888.com/base/recharge-rust-base:latest
            WORKDIR /app
            COPY ./target/release/${projectName} /app/${projectName}
            COPY ./crates /app/crates
            RUN echo "当前目录是:" && pwd && \\
                echo "检查 ${projectName} 是否存在:" && \\
                ls -l /app/*
            CMD ["./${projectName}", "./crates/${projectName}"]
          """.stripIndent()

          script.configFileProvider([configFile(fileId: "${script.env.REGISTRY_CREDNTIAL}", targetLocation: "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker/config.json")]) {
            script.withEnv(["DOCKER_CONFIG=${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker"]) {
              try {
                script.dir(path) {
                  script.writeFile file: 'Dockerfile', text: dockerfileContent
                  runBuildImage(image_addr.toString())
                }
                script.echo "${Colors.GREEN}✅ 成功构建并推送镜像: ${image_addr}${Colors.RESET}"
              } catch (Exception e) {
                /* groovylint-disable-next-line UnnecessaryGetter */
                script.echo "${Colors.RED}错误：无法为模块 ${mod} 构建镜像 ${image_addr}，错误信息：${e}${Colors.RESET}"
                script.error '❌ 镜像构建失败，请检查构建环境或 Dockerfile 配置'
              }
            }
          }
        } else {
          // 独立模块的镜像构建
          for (mod in module_list) {
            def subpath = app_module[mod]?.toString() ?: ''
            def path = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/${subpath}"
            def image_addr = "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}-${mod}:${image_tag}"
            def projectName = "${script.env.JOB_PREFIX}-${mod}"
            def dockerfileContent = """
              FROM hub.rancher8888.com/base/recharge-rust-base:latest
              WORKDIR /app
              COPY ./target/release/${projectName} /app/${projectName}
              COPY ./crates /app/crates
              CMD ["./${projectName}", "./crates/${projectName}"]
            """.stripIndent()

            script.configFileProvider([configFile(fileId: "${script.env.REGISTRY_CREDNTIAL}", targetLocation: "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker/config.json")]) {
              script.withEnv(["DOCKER_CONFIG=${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker"]) {
                try {
                  script.dir(path) {
                    writeFile file: 'Dockerfile', text: dockerfileContent
                    runBuildImage(image_addr.toString())
                  }
                  script.echo "${Colors.GREEN}✅ 成功构建并推送镜像: ${image_addr}${Colors.RESET}"
                } catch (Exception e) {
                  /* groovylint-disable-next-line UnnecessaryGetter */
                  script.echo "${Colors.RED}错误：无法为模块 ${mod} 构建镜像 ${image_addr}，错误信息：${e}${Colors.RESET}"
                  script.error '❌ 镜像构建失败，请检查构建环境或 Dockerfile 配置'
                }
              }
            }
          }
        }
        break
      default:
        if (script.env.SHARED_MODULE == 'true') {
          // 共享模块的镜像构建
          def first_mod = module_list[0]
          def subpath = app_module[first_mod]?.toString() ?: ''
          def path = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/${subpath}"
          def image_addr = "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}:${image_tag}"

          // 根据不同编译环境的上下文执行
          // 文件操作必须为 job 的 ROOT_WORKSPACE 下，否则没权限
          script.configFileProvider([configFile(fileId: "${script.env.REGISTRY_CREDNTIAL}", targetLocation: "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker/config.json")]) {
            script.withEnv(["DOCKER_CONFIG=${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker"]) {
              try {
                script.dir(path) {
                  runBuildImage(image_addr.toString())
                }
                script.echo "${Colors.GREEN}✅ 成功构建并推送镜像: ${image_addr}${Colors.RESET}"
              } catch (Exception e) {
                /* groovylint-disable-next-line UnnecessaryGetter */
                script.echo "${Colors.RED}错误：无法为模块 ${mod} 构建镜像 ${image_addr}，错误信息：${e}${Colors.RESET}"
                script.error '❌ 镜像构建失败，请检查构建环境或 Dockerfile 配置'
              }
            }
          }
        } else {
          // 独立模块的镜像构建
          for (mod in module_list) {
            def subpath = app_module[mod]?.toString() ?: ''
            def path = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/${subpath}"
            def image_addr = "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}-${mod}:${image_tag}"

            script.configFileProvider([configFile(fileId: "${script.env.REGISTRY_CREDNTIAL}", targetLocation: "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker/config.json")]) {
              script.withEnv(["DOCKER_CONFIG=${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/.docker"]) {
                try {
                  script.dir(path) {
                    runBuildImage(image_addr.toString())
                  }
                  script.echo "${Colors.GREEN}✅ 成功构建并推送镜像: ${image_addr}${Colors.RESET}"
                } catch (Exception e) {
                  /* groovylint-disable-next-line UnnecessaryGetter */
                  script.echo "${Colors.RED}错误：无法为模块 ${mod} 构建镜像 ${image_addr}，错误信息：${e}${Colors.RESET}"
                  script.error '❌ 镜像构建失败，请检查构建环境或 Dockerfile 配置'
                }
              }
            }
          }
        }
    }
  }

  def runBuildImage(image_addr) {
    String baseImage = script.env.BASE_IMAGE.toString()
    
    if (!script.fileExists('Dockerfile')) {
      script.error "❌ Dockerfile 不存在"
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

    def status = script.sh(script: """
      set -e
      ${buildCommand}
    """, returnStatus: true)

    if (status == 0) {
      script.env.IMAGE_UPLOAD_SUCCESS = 'true'
    } else {
      script.env.IMAGE_UPLOAD_SUCCESS = 'false'
    }
  }

}