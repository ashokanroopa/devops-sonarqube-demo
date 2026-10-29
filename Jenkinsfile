pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

    parameters {
        gitParameter(
            name: 'BRANCH_NAME',
            type: 'PT_BRANCH',
            branch: 'main',
            branchFilter: 'origin/(.*)',
            tagFilter: '*',
            defaultValue: 'main',
            selectedValue: 'DEFAULT',
            sortMode: 'DESCENDING_SMART',
            quickFilterEnabled: true,
            useRepository: 'https://github.com/ashokanroopa/devops-sonarqube-demo.git',
            description: 'Select the GitHub branch to build'
        )
    }

    environment {
        GIT_URL = 'https://github.com/ashokanroopa/devops-sonarqube-demo.git'

        SONAR_SERVER = 'SonarQube-Server'
        SONAR_TOKEN_CREDENTIALS = 'sonarqube-token1'

        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = '974066991334'
        ECR_REPO = 'devops-sonarqube-demo'
        IMAGE_TAG = "v${BUILD_NUMBER}"
        ECR_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"

        EC2_CREDENTIALS = 'ec2-ssh-key'
        EC2_USER = 'ubuntu'
        EC2_HOST = '172.31.3.250'
        APP_DIR = '/home/ubuntu/devops-sonarqube-demo'

        LOKI_URL = 'http://172.31.10.97:3100/loki/api/v1/push'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()

                script {
                    def selectedBranch = params.BRANCH_NAME ?: 'main'

                    echo "Selected branch: ${selectedBranch}"

                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${selectedBranch}"]],
                        userRemoteConfigs: [[
                            url: "${env.GIT_URL}"
                        ]]
                    ])
                }

                sh '''
                    echo "Checked out commit:"
                    git rev-parse --short HEAD
                    echo "Current branch:"
                    git branch --show-current
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withCredentials([
                    string(
                        credentialsId: "${SONAR_TOKEN_CREDENTIALS}",
                        variable: 'SONAR_TOKEN'
                    )
                ]) {
                    withSonarQubeEnv("${SONAR_SERVER}") {
                        sh '''
                            mvn -B clean verify sonar:sonar \
                              -Dsonar.token="$SONAR_TOKEN" \
                              -Dsonar.projectKey=devops-sonarqube-demo \
                              -Dsonar.projectName=devops-sonarqube-demo
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    script {
                        def qualityGate = waitForQualityGate()

                        if (qualityGate.status != 'OK') {
                            error "SonarQube Quality Gate failed: ${qualityGate.status}"
                        }

                        echo "SonarQube Quality Gate: ${qualityGate.status}"
                    }
                }
            }
        }

        stage('Approval') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    input(
                        message: "Approve deployment of ${env.IMAGE_TAG} to application EC2?",
                        ok: 'APPROVE'
                    )
                }
            }
        }

        stage('Maven Build') {
            steps {
                sh '''
                    mvn -B clean package
                    echo "Maven package completed."
                '''
            }
        }

        stage('Build and Deploy on Application Server') {
            steps {
                script {
                    def selectedBranch = params.BRANCH_NAME ?: 'main'

                    withEnv(["SELECTED_BRANCH=${selectedBranch}"]) {
                        sshagent(credentials: ["${EC2_CREDENTIALS}"]) {
                            sh '''
                                set -eu

                                echo "Connecting to application EC2..."

                                ssh -o StrictHostKeyChecking=accept-new \
                                    "${EC2_USER}@${EC2_HOST}" \
                                    "mkdir -p '${APP_DIR}'"

                                ssh -o StrictHostKeyChecking=accept-new \
                                    "${EC2_USER}@${EC2_HOST}" \
                                    "set -eu

                                     if [ -d '${APP_DIR}/.git' ]; then
                                         cd '${APP_DIR}'
                                         git fetch origin
                                         git checkout '${SELECTED_BRANCH}'
                                         git reset --hard 'origin/${SELECTED_BRANCH}'
                                     else
                                         git clone \
                                           --branch '${SELECTED_BRANCH}' \
                                           '${GIT_URL}' \
                                           '${APP_DIR}'
                                         cd '${APP_DIR}'
                                     fi

                                     mvn -B clean package

                                     aws ecr get-login-password \
                                       --region '${AWS_REGION}' |
                                       docker login \
                                         --username AWS \
                                         --password-stdin \
                                         '${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com'

                                     docker build \
                                       -t '${ECR_REPO}:${IMAGE_TAG}' .

                                     docker tag \
                                       '${ECR_REPO}:${IMAGE_TAG}' \
                                       '${ECR_URI}:${IMAGE_TAG}'

                                     docker push '${ECR_URI}:${IMAGE_TAG}'

                                     docker pull '${ECR_URI}:${IMAGE_TAG}'

                                     docker stop devops-app || true
                                     docker rm devops-app || true

                                     docker run -d \
                                       --name devops-app \
                                       --restart unless-stopped \
                                       -p 8080:8080 \
                                       '${ECR_URI}:${IMAGE_TAG}'

                                     echo 'Deployment completed.'
                                     docker ps --filter name=devops-app
                                    "
                            '''
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        }

        failure {
            echo "Pipeline FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        }

        aborted {
            echo "Pipeline ABORTED: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        }

        always {
            script {
                def buildStatus = currentBuild.currentResult ?: 'UNKNOWN'
                def selectedBranch = params.BRANCH_NAME ?: 'unknown'
                def image = "${env.ECR_URI}:${env.IMAGE_TAG}"
                def timestampNs = "${System.currentTimeMillis()}000000"

                withEnv([
                    "LOKI_STATUS=${buildStatus}",
                    "LOKI_JOB=${env.JOB_NAME}",
                    "LOKI_BUILD=${env.BUILD_NUMBER}",
                    "LOKI_BRANCH=${selectedBranch}",
                    "LOKI_IMAGE=${image}",
                    "LOKI_TIMESTAMP=${timestampNs}"
                ]) {
                    sh(
                        returnStatus: true,
                        script: '''
                            set +e

                            PAYLOAD=$(python3 -c '
import json
import os

message = (
    "status=" + os.environ["LOKI_STATUS"]
    + " job=" + os.environ["LOKI_JOB"]
    + " build=" + os.environ["LOKI_BUILD"]
    + " branch=" + os.environ["LOKI_BRANCH"]
    + " image=" + os.environ["LOKI_IMAGE"]
)

payload = {
    "streams": [{
        "stream": {
            "job": "jenkins-pipeline",
            "app": "devops-sonarqube-demo"
        },
        "values": [[os.environ["LOKI_TIMESTAMP"], message]]
    }]
}

print(json.dumps(payload))
')

                            if [ -z "$PAYLOAD" ]; then
                                echo "Could not generate Loki payload."
                                exit 0
                            fi

                            HTTP_CODE=$(curl \
                                --connect-timeout 3 \
                                --max-time 5 \
                                -sS \
                                -o /tmp/loki-response.txt \
                                -w '%{http_code}' \
                                -X POST "$LOKI_URL" \
                                -H 'Content-Type: application/json' \
                                --data-binary "$PAYLOAD")

                            CURL_STATUS=$?

                            echo "Loki HTTP status: ${HTTP_CODE:-unavailable}"

                            if [ "$CURL_STATUS" -ne 0 ]; then
                                echo "Loki request failed; pipeline result unchanged."
                            elif [ "$HTTP_CODE" != "204" ]; then
                                echo "Loki did not return HTTP 204."
                                if [ -f /tmp/loki-response.txt ]; then
                                    cat /tmp/loki-response.txt
                                fi
                            else
                                echo "Pipeline summary successfully sent to Loki."
                            fi

                            rm -f /tmp/loki-response.txt
                            exit 0
                        '''
                    )
                }
            }
        }
    }
}
