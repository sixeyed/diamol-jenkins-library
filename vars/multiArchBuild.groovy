def getDockerfileName(name) {
  if ((name?.trim()) as boolean) {
    return name
  } else {
    return 'Dockerfile'
 }
}

def call(Map config) {
    pipeline {
        agent any
        stages {
            stage('build') {
                environment {
                    REPO_NAME = "${JOB_NAME}"
                    BUILD_CONTEXT = "${config.linuxContext}"
                    WINDOWS_BUILD_CONTEXT = "${config.windowsContext}"
                    DOCKERFILE = getDockerfileName(config.dockerfile)
                }
                parallel {
                    stage('linux-arm64') {                        
                        steps {
                            script{
                                docker.withServer("tcp://${DOCKER_LINUX_ARM64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "-f ${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }
                                }
                            }
                        }
                    }
                    stage('linux-arm') {                        
                        steps {
                            script{
                                 docker.withServer("tcp://${DOCKER_LINUX_ARM}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "-f ${DOCKERFILE} ${BUILD_CONTEXT}")                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }      
                                }
                            }
                        }
                    }
                    stage('linux-amd64') {                        
                        steps {
                            script{
                                docker.withServer("tcp://${DOCKER_LINUX_AMD64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "-f ${DOCKERFILE} ${BUILD_CONTEXT}")
                                    withDockerRegistry([credentialsId: "docker-hub", url: "" ]) {        
                                        image.push()
                                    }       
                                }
                            }
                        }
                    }                
                    stage('windows-amd64') {                        
                        steps {
                            script {
                                docker.withServer("tcp://${DOCKER_WINDOWS_AMD64}:2376", 'docker-client') {
                                    def image = docker.build("${REPO_NAME}:${STAGE_NAME}", "-f ${DOCKERFILE} ${WINDOWS_BUILD_CONTEXT}")
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