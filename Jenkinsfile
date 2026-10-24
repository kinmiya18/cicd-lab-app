// Declarative pipeline: GitHub webhook -> test -> image -> (scan) -> Docker Hub -> bump tag in GitOps repo.
// Fleet watches the GitOps repo and rolls the new tag out with Helm; Jenkins never talks to the cluster.
//
// Jenkins prerequisites: JDK 17, Docker CLI on the agent, Git, "GitHub plugin" for the webhook trigger.
// Credentials (Manage Jenkins > Credentials):
//   dockerhub-creds    : Username with password; Docker Hub username + access token (Read & Write)
//   gitops-deploy-key  : SSH private key; its public half is a write-enabled deploy key on the GitOps repo
pipeline {
    agent any

    parameters {
        booleanParam(name: 'RUN_TRIVY', defaultValue: false,
                     description: 'Scan the image with Trivy (run via the aquasec/trivy container) and fail on fixable CRITICAL findings')
    }

    environment {
        DOCKERHUB_USER = 'kienphamtrung'
        IMAGE_NAME     = 'demo-app'
        GITOPS_REPO    = 'git@github.com:kinmiya18/cicd-lab-gitops.git'
    }

    triggers {
        githubPush()
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Prepare') {
            steps {
                script {
                    env.GIT_SHA = sh(returnStdout: true, script: 'git rev-parse --short=7 HEAD').trim()
                    env.IMAGE   = "${DOCKERHUB_USER}/${IMAGE_NAME}:${env.GIT_SHA}"
                }
                echo "Building ${env.IMAGE}"
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B clean verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker build') {
            steps {
                sh 'docker build --build-arg GIT_SHA=${GIT_SHA} -t ${IMAGE} .'
            }
        }

        stage('Image scan (Trivy)') {
            when { expression { params.RUN_TRIVY } }
            steps {
                // The vulnerability DBs are large; keep them in a named volume so only the first scan downloads them.
                sh '''docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache aquasec/trivy:latest \
                      image --scanners vuln --timeout 15m --exit-code 1 --severity CRITICAL --ignore-unfixed --no-progress ${IMAGE}'''
            }
        }

        stage('Push to Docker Hub') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'HUB_USER',
                                                  passwordVariable: 'HUB_TOKEN')]) {
                    sh '''
                        echo "$HUB_TOKEN" | docker login -u "$HUB_USER" --password-stdin
                        docker push "$IMAGE"
                        docker logout
                    '''
                }
            }
        }

        stage('Bump tag in GitOps repo') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'gitops-deploy-key',
                                                   keyFileVariable: 'SSH_KEY')]) {
                    sh '''
                        export GIT_SSH_COMMAND="ssh -i $SSH_KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"
                        rm -rf gitops-work
                        git clone "${GITOPS_REPO}" gitops-work
                        cd gitops-work
                        sed -i "s|^  tag: .*|  tag: \\"${GIT_SHA}\\"|" demo-app/values.yaml
                        git config user.name  "jenkins-ci"
                        git config user.email "jenkins-ci@users.noreply.github.com"
                        git add demo-app/values.yaml
                        git commit -m "ci: bump demo-app image tag to ${GIT_SHA}" || echo "tag unchanged, nothing to commit"
                        git push origin HEAD
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'rm -rf gitops-work'
            sh 'docker rmi ${IMAGE} || true'
        }
    }
}
