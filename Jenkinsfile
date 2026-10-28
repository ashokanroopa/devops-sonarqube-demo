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
            branchFilter: 'origin/(.*)',
            defaultValue: 'main',
            selectedValue: 'DEFAULT',
            sortMode: 'DESCENDING_SMART',
            description: 'Select the GitHub branch to build'
        )
    }

    environment {
        // GitHub
        GIT_URL = 'https://github.com/ashokanroopa/devops-sonarqube-demo.git'

        // SonarQube
        SONARQUBE_SERVER = 'SonarQube-Server'
        SONAR_TOKEN_CREDENTIAL = 'sonarqube-token1'

        // AWS / ECR
        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = '974066991334'
        ECR_REPOSITORY = 'devops-sonarqube-demo'
        IMAGE_TAG = "v${BUILD_NUMBER}"
        ECR_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}"

        // Application EC2
        EC2_SSH_CREDENTIALS = 'ec2-ssh-key'
        EC2_HOST = '172.31.3.250'
        EC2_USER = 'ubuntu'
        APP_DIRECTORY = '/home/ubuntu/devops-sonarqube-demo'

        // Loki runs on the Jenkins EC2 server
        LOKI_URL = 'http://172.31.10.97:3100/loki/api/v1/push'
    }

    stages {
        stage('Fetch Branch') {
            steps {
                echo 'Fetching branch information'

                deleteDir()

                git(
                    branch: 'main',
                    url: "${GIT_URL}"
                )

                sh '''
                    git fetch --all --prune
                    echo "Available branches:"
                    git branch -r
                '''

                echo "Selected branch: ${params.BRANCH_NAME}"
            }
        }

        stage('Checkout') {
            steps {
                echo 'Checking out selected branch'

                sh '''
                    set -e
                    git checkout -B "$BRANCH_NAME" "origin/$BRANCH_NAME"

                    echo "Checked out branch:"
                    git branch --show-current

                    echo "Commit:"
                    git rev-parse HEAD
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo 'Starting SonarQube analysis'

                withSonarQubeEnv("${SONARQUBE_SERVER}") {
                    withCredentials([
                        string(
                            credentialsId: "${SONAR_TOKEN_CREDENTIAL}",
                            variable: 'SONAR_TOKEN'
                        )
                    ]) {
                        sh '''
                            set -e
                            mvn clean verify sonar:sonar \
                                -Dsonar.projectKey=devops-sonarqube-demo \
                                -Dsonar.projectName=devops-sonarqube-demo \
                                -Dsonar.token="$SONAR_TOKEN"
                        '''
                    }
                }

                echo 'SonarQube analysis completed'
            }
        }

        stage('Quality Gate') {
            steps {
                echo 'Waiting for SonarQube quality gate'

                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }

                echo 'CODE QUALITY GATE PASSED'
            }
        }

        stage('Approval') {
            steps {
                script {
                    def approval = input(
                        message: 'Do you approve the deployment?',
                        parameters: [
                            choice(
                                name: 'APPROVAL',
                                choices: 'APPROVE\nDENY',
                                description: 'Select APPROVE to continue or DENY to stop'
                            )
                        ]
                    )

                    echo "Approval decision: ${approval}"

                    if (approval != 'APPROVE') {
                        error('Deployment denied by approver')
                    }

                    echo 'DEPLOYMENT APPROVED'
                }
            }
        }

        stage('Maven Build') {
            steps {
                echo 'Building application on Jenkins'

                sh '''
                    set -e
                    mvn clean package -DskipTests
                '''

                echo 'JENKINS MAVEN BUILD SUCCESSFUL'
            }
        }

        stage('Build and Deploy on Application Server') {
            steps {
                echo 'Starting application server deployment'

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${EC2_SSH_CREDENTIALS}",
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USERNAME'
                    )
                ]) {
                    sh '''
                        set -e

                        echo "Application server: $EC2_HOST"
                        echo "Image: $ECR_URI:$IMAGE_TAG"

                        ssh -o StrictHostKeyChecking=no \
                            -i "$SSH_KEY" \
                            "$SSH_USERNAME@$EC2_HOST" \
                            "AWS_REGION='$AWS_REGION' \
                             ECR_URI='$ECR_URI' \
                             ECR_REPOSITORY='$ECR_REPOSITORY' \
                             IMAGE_TAG='$IMAGE_TAG' \
                             APP_DIRECTORY='$APP_DIRECTORY' \
                             BRANCH_NAME='$BRANCH_NAME' \
                             bash -s" <<'REMOTE_SCRIPT'

set -e

cd "$APP_DIRECTORY"

echo "Fetching selected branch..."
git fetch origin
git checkout -B "$BRANCH_NAME" "origin/$BRANCH_NAME"
git pull --ff-only origin "$BRANCH_NAME"

echo "Building Maven application..."
mvn clean package -DskipTests

echo "Building Docker image..."
docker build -t "${ECR_REPOSITORY}:${IMAGE_TAG}" .

echo "Logging into ECR..."
aws ecr get-login-password --region "$AWS_REGION" |
    docker login --username AWS --password-stdin "$ECR_URI"

echo "Tagging image..."
docker tag \
    "${ECR_REPOSITORY}:${IMAGE_TAG}" \
    "${ECR_URI}:${IMAGE_TAG}"

echo "Pushing image to ECR..."
docker push "${ECR_URI}:${IMAGE_TAG}"

echo "Stopping old container if present..."
docker stop devops-app || true
docker rm devops-app || true

echo "Pulling image from ECR..."
docker pull "${ECR_URI}:${IMAGE_TAG}"

echo "Starting new container..."
docker run -d \
    --name devops-app \
    -p 8080:8080 \
    "${ECR_URI}:${IMAGE_TAG}"

echo "Deployment completed"
docker ps
docker logs --tail 30 devops-app

REMOTE_SCRIPT
                    '''
                }
            }
        }
    }

    post {
        success {
            echo """
            ========================================
                    PIPELINE SUCCESS
            ========================================
            Branch: ${params.BRANCH_NAME}
            Quality Gate: PASSED
            Image: ${ECR_URI}:${IMAGE_TAG}
            Application Server: ${EC2_HOST}
            Container: devops-app
            ========================================
            """
        }

        failure {
            echo """
            ========================================
                    PIPELINE FAILED
            ========================================
            Branch: ${params.BRANCH_NAME}
            Application Server: ${EC2_HOST}
            Check Jenkins Console Output.
            ========================================
            """
        }

        aborted {
            echo """
            ========================================
                    PIPELINE ABORTED
            ========================================
            Branch: ${params.BRANCH_NAME}
            ========================================
            """
        }

        always {
            script {
                // Record one final pipeline summary event in Loki.
                // Logging errors must not override the pipeline result.

                def buildStatus = currentBuild.currentResult ?: 'UNKNOWN'
                def selectedBranch = params.BRANCH_NAME ?: 'unknown'
                def image = env.ECR_URI + ':' + env.IMAGE_TAG

                def logMessage =
                    "status=${buildStatus} job=${env.JOB_NAME} " +
                    "build=${env.BUILD_NUMBER} branch=${selectedBranch} " +
                    "image=${image}"

                def timestampNs =
                    (System.currentTimeMillis().toString() + '000000')

                def payload = [
                    streams: [[
                        stream: [
                            job: 'jenkins-pipeline',
                            app: 'devops-sonarqube-demo'
                        ],
                        values: [[timestampNs, logMessage]]
                    ]]
                ]

                try {
                    writeFile(
                        file: 'loki-payload.json',
                        text: JsonOutput.toJson(payload)
                    )

                    echo "Sending pipeline summary to Loki: ${buildStatus}"

                    sh(
                        returnStatus: true,
                        script: '''
                            set +e

                            HTTP_CODE=$(curl \
                                --connect-timeout 3 \
                                --max-time 5 \
                                -sS \
                                -o loki-response.txt \
                                -w '%{http_code}' \
                                -X POST "$LOKI_URL" \
                                -H 'Content-Type: application/json' \
                                --data-binary @loki-payload.json)

                            CURL_STATUS=$?

                            echo "Loki HTTP status: ${HTTP_CODE:-unavailable}"

                            if [ -f loki-response.txt ]; then
                                cat loki-response.txt
                            fi

                            if [ "$CURL_STATUS" -ne 0 ]; then
                                echo "Loki request failed; pipeline result is unchanged"
                            fi

                            rm -f loki-payload.json loki-response.txt
                            exit 0
                        '''
                    )
                } catch (Exception e) {
                    echo "Loki logging could not be completed: ${e.getMessage()}"
                    echo 'Continuing without changing the pipeline result.'
                }
            }
        }
    }
}
