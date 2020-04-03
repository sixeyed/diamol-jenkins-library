def getDockerfileName(String name) {
  if ((name?.trim()) as boolean) {
    return name
  } else {
    return 'Dockerfile'
 }
}

def getBuildContext(Map config, String architecture) {
  if (architecture=='windows-amd64') {
    return config.windowsContext
  }
  if (architecture=='linux-arm64' && (config.linuxArm64Context?.trim()) as boolean) {
    return config.linuxArm64Context
  }
  if (architecture=='linux-arm' && (config.linuxArmContext?.trim()) as boolean) {
    return config.linuxArmContext
  }
  return config.linuxContext
}

def call(Map config) {
    pipeline {
        agent any
        stages {
            stage('build') {
                environment {
                    REPO_NAME = "${JOB_NAME}"
                    DOCKERFILE = getDockerfileName(config.dockerfile)
                }
                parallel {
                    stage('linux-arm64') {
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, ${STAGE_NAME})
                        }                   
                        steps {
                            script{
                                docker.withServer("tcp://${DOCKER_LINUX_ARM64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }
                                }
                            }
                        }
                    }
                    stage('linux-arm') { 
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, ${STAGE_NAME})
                        }                         
                        steps {
                            script{
                                 docker.withServer("tcp://${DOCKER_LINUX_ARM}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }      
                                }
                            }
                        }
                    }
                    stage('linux-amd64') {         
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, ${STAGE_NAME})
                        }                 
                        steps {
                            script{
                                docker.withServer("tcp://${DOCKER_LINUX_AMD64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }       
                                }
                            }
                        }
                    }                
                    stage('windows-amd64') {  
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, ${STAGE_NAME})
                        }                        
                        steps {
                            script {
                                docker.withServer("tcp://${DOCKER_WINDOWS_AMD64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }     
                                }
                            }
                        }
                    }
                }
            }
            stage('manifest') {
                steps {
                    sh """
                        docker manifest create --amend $JOB_NAME \
                            $JOB_NAME:windows-amd64 $JOB_NAME:linux-amd64 $JOB_NAME:linux-arm64 $JOB_NAME:linux-arm
    
                        docker manifest inspect $JOB_NAME
                    """
                    //can't do this because plugin writes its own config file
                    // and we need experimental mode to push manifests
                    //withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                    //    sh """
                    //        docker manifest push $JOB_NAME
                    //    """
                    // }
                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_HUB_USER', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
                        sh '''
                            docker login -u "$DOCKER_HUB_USER" -p "$DOCKER_HUB_PASSWORD"
                            docker manifest push "$JOB_NAME"
                            docker logout
                        '''
                    }
                }
            }
        }
    }
}