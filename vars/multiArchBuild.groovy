def getTag(String tag) {
  if ((tag?.trim()) as boolean) {
    return tag + '-'
  } else {
    return ''
 }
}

def getManifestTag(String tag) {
  if ((tag?.trim()) as boolean) {
    return tag
  } else {
    return 'latest'
 }
}

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
        environment {
            REPO_NAME = "${JOB_NAME}"
            TAG = getTag(config.tag)
        }
        stages {
            stage('build') {
                environment {
                    DOCKERFILE = getDockerfileName(config.dockerfile)
                }
                parallel {
                    stage('linux-arm64') {
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, env.STAGE_NAME)
                        }                   
                        steps {
                            script{
                                docker.withServer("tcp://${DOCKER_LINUX_ARM64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${TAG}${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }
                                }
                            }
                        }
                    }
                    stage('linux-arm') { 
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, env.STAGE_NAME)
                        }                         
                        steps {
                            script{
                                 docker.withServer("tcp://${DOCKER_LINUX_ARM}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${TAG}${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }      
                                }
                            }
                        }
                    }
                    stage('linux-amd64') {         
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, env.STAGE_NAME)
                        }                 
                        steps {
                            script{
                                docker.withServer("tcp://${DOCKER_LINUX_AMD64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${TAG}${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }       
                                }
                            }
                        }
                    }                
                    stage('windows-amd64') {  
                        environment {
                            BUILD_CONTEXT = getBuildContext(config, env.STAGE_NAME)
                        }                        
                        steps {
                            script {
                                docker.withServer("tcp://${DOCKER_WINDOWS_AMD64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${TAG}${STAGE_NAME}", "--pull -f ${BUILD_CONTEXT}/${DOCKERFILE} ${BUILD_CONTEXT}")
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
                environment {
                    MANIFEST_TAG = getManifestTag(config.tag)
                }
                steps {
                    sh """
                        docker manifest create --amend $REPO_NAME:$MANIFEST_TAG \
                            $REPO_NAME:${TAG}windows-amd64 $REPO_NAME:${TAG}linux-amd64 $REPO_NAME:${TAG}linux-arm64 $REPO_NAME:${TAG}linux-arm
    
                        docker manifest inspect $REPO_NAME:$MANIFEST_TAG
                    """
                    //can't do this because plugin writes its own config file
                    // and we need experimental mode to push manifests
                    //withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                    //    sh """
                    //        docker manifest push ...
                    //    """
                    // }
                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_HUB_USER', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
                        sh '''
                            docker login -u "$DOCKER_HUB_USER" -p "$DOCKER_HUB_PASSWORD"
                            docker manifest push "$REPO_NAME:$MANIFEST_TAG"
                            docker logout
                        '''
                    }
                }
            }
        }
    }
}