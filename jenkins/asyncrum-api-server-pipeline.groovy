pipeline {
    agent any

    environment {
        imagename = "wlghks0314/asyncrum-api-server"
        registryCredential = 'dockerhub'
        dockerImage = ''
        githubToken = credentials('github-token')
    }

    stages {
        stage('Prepare') {
          steps {
            echo 'Clonning Repository'
            git url: 'https://github.com/Whiteboard-Journey/asyncrum-backend.git',
              branch: 'main',
              credentialsId: 'github'
            }
            post {
             success { 
               echo 'Successfully Cloned Repository'
             }
           	 failure {
               error 'Failed Cloned Repository'
             }
          }
        }

        stage('Bulid Gradle') {
          steps {
            echo 'Bulid Gradle'
            dir('.'){
                sh 'chmod +x gradlew'
                sh 'cp /var/jenkins_home/workspace/prod/asyncrum_firebase_service_key.json ./src/main/resources/asyncrum_firebase_service_key.json'
                sh './gradlew clean build'
            }
          }
          post {
            success { 
               echo 'Successfully Gradle Build Jar'
             }
            failure {
              error 'Failed Gradle Build Jar'
            }
          }
        }
        
        // Report Code Coverage stage is only for webhook triggered by new commit
        // stage('Report Code Coverage') {
        //   steps {
        //     echo 'Report Code Coverage'
        //     dir('.'){
        //         sh 'curl -Os https://uploader.codecov.io/latest/linux/codecov'
        //         sh 'chmod +x codecov'
        //         sh './codecov -t ${CODECOV_TOKEN} --branch main --sha --verbose'
        //     }
        //   }
        //   post {
        //     success { 
        //       echo 'Successfully Report Code Coverage'
        //      }
        //     failure {
        //       error 'Failed Report Code Coverage'
        //     }
        //   }
        // }
        
        stage('Bulid Docker') {
          steps {
            echo 'Bulid Docker'
            script {
                dockerImage = docker.build(imagename + ":$BUILD_NUMBER")
            }
          }
          post {
            success { 
               echo 'Successfully Build Docker Image'
             }
            failure {
              error 'Failed Build Docker Image'
            }
          }
        }

        stage('Push Docker') {
          steps {
            echo 'Push Docker'
            script {
                docker.withRegistry( '', registryCredential) {
                    dockerImage.push() 
                }
            }
          }
          post {
            always {
              cleanWs(deleteDirs: true)
            }
            success {
               echo 'Successfully Push Docker Image'
            }
            failure {
              error 'Failed Push Docker Image'
            }
          }
        }
        
        stage('K8s Manifest Update') {
            steps {
                echo 'K8s Manifest Update'
                
                git url: 'https://github.com/Whiteboard-Journey/asyncrum-deployment.git',
                branch: 'main',
                credentialsId: 'github'
                
                sh "git config --global user.email 'wlghks0314@gmail.com'"
                sh "git config --global user.name 'Kevin Park'"
              
                sh "sed -i 's/asyncrum-api-server:.*/asyncrum-api-server:$BUILD_NUMBER/g' asyncrum-api/deployment/asyncrum-api-deployment.yaml"
                sh "git add asyncrum-api/deployment/asyncrum-api-deployment.yaml"
                sh "git commit -m '[Update] Asyncrum API Server $BUILD_NUMBER image version update'"
                
                sh "git remote set-url origin https://Krapi0314:$githubToken@github.com/Whiteboard-Journey/asyncrum-deployment.git"
                sh "git push origin main"
                
            }
            post {
                always {
                    cleanWs(deleteDirs: true)
                }
                success { 
                   echo 'Successfully Update K8s Manifest'
                 }
                failure {
                  error 'Failed Update K8s Manifest'
                }
            }
        }
        
        // stage('Docker Run') {
        //     steps {
        //         echo 'Pull Docker Image & Docker Image Run'
        //         sshagent (credentials: ['ssh']) {
        //             sh "ssh -o StrictHostKeyChecking=no ec2-user@10.1.5.155 'docker pull wlghks0314/asyncrum-api-server:latest'" 
        //             sh "ssh -o StrictHostKeyChecking=no ec2-user@10.1.5.155 'docker ps -q --filter name=asyncrum-api-server'"
        //             sh "ssh -o StrictHostKeyChecking=no ec2-user@10.1.5.155 'docker rm -f \$(docker ps -aq --filter name=asyncrum-api-server)'"
        //             sh "ssh -o StrictHostKeyChecking=no ec2-user@10.1.5.155 'docker run -d --name asyncrum-api-server -p 8080:8080 --restart=unless-stopped wlghks0314/asyncrum-api-server:latest'"
        //             sh "ssh -o StrictHostKeyChecking=no ec2-user@10.1.5.155 'docker rmi -f \$(docker images -q -f dangling=true)'"
        //         }
        //     }
        // }
    }
    post {
        success {
            slackSend (channel: '#서버배포', color: '#00FF00', message: "BUILD SUCCESS: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        }
        failure {
            slackSend (channel: '#서버배포', color: '#FF0000', message: "BUILD FAIL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        }
    }
}