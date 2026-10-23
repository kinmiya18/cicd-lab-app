// Declarative pipeline: GitHub webhook -> test -> image -> (scan) -> Harbor -> bump tag in GitOps repo.
// Fleet watches the GitOps repo and rolls the new tag out with Helm; Jenkins never talks to the cluster.
//
// Jenkins prerequisites: JDK 17, Docker CLI on the agent, Git, "GitHub plugin" for the webhook trigger.
// Credentials (Manage Jenkins > Credentials):
//   harbor-creds       : Username with password, Harbor account that can push to HARBOR_PROJECT
//   gitops-deploy-key  : SSH private key; its public half is a write-enabled deploy key on the GitOps repo
pipeline {
    agent any

    parameters {
        booleanParam(name: 'RUN_TRIVY', defaultValue: false,
                     description: 'Scan the image with Trivy (run via the aquasec/trivy container) and fail on fixable CRITICAL findings')
    }

    environment {
        HARBOR_HOST    = '127.0.0.1:5000'
        HARBOR_PROJECT = 'demo'
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
                    env.IMAGE   = "${HARBOR_HOST}/${HARBOR_PROJECT}/${IMAGE_NAME}:${env.GIT_SHA}"
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
                sh '''docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest \
                      image --exit-code 1 --severity CRITICAL --ignore-unfixed --no-progress ${IMAGE}'''
            }
        }

        stage('Push to Harbor') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'harbor-creds',
                                                  usernameVariable: 'HARBOR_USER',
                                                  passwordVariable: 'HARBOR_PASS')]) {
                    sh '''
                        echo "$HARBOR_PASS" | docker login "$HARBOR_HOST" -u "$HARBOR_USER" --password-stdin
                        docker push "$IMAGE"
                        docker logout "$HARBOR_HOST"
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
