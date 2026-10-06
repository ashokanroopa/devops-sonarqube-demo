pipeline {

    agent any

    options {
        skipDefaultCheckout(true)
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

        // =====================================================
        // GitHub
        // =====================================================

        GIT_URL = 'https://github.com/ashokanroopa/devops-sonarqube-demo.git'


        // =====================================================
        // SonarQube
        // =====================================================

        SONARQUBE_SERVER = 'SonarQube-Server'
        SONAR_TOKEN_CREDENTIAL = 'sonarqube-token1'


        // =====================================================
        // AWS / ECR
        // =====================================================

        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = '974066991334'
        ECR_REPOSITORY = 'devops-sonarqube-demo'

        IMAGE_TAG = "v${BUILD_NUMBER}"

        ECR_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}"


        // =====================================================
        // Application EC2
        // =====================================================

        EC2_SSH_CREDENTIALS = 'ec2-ssh-key'

        EC2_HOST = '172.31.3.250'

        EC2_USER = 'ubuntu'

        APP_DIRECTORY = '/home/ubuntu/devops-sonarqube-demo'
    }


    stages {

        // =====================================================
        // 1. Fetch Branch
        // =====================================================

        stage('Fetch Branch') {

            steps {

                echo "========================================"
                echo "Fetching Branch Information"
                echo "========================================"

                deleteDir()

                git(
                    branch: 'main',
                    url: "${GIT_URL}"
                )

                sh '''
                    echo "Fetching all branches..."

                    git fetch --all --prune

                    echo ""
                    echo "Available branches:"

                    git branch -r
                '''

                echo "Selected Branch: ${params.BRANCH_NAME}"
            }
        }


        // =====================================================
        // 2. Checkout Selected Branch
        // =====================================================

        stage('Checkout') {

            steps {

                echo "========================================"
                echo "Checking Out Selected Branch"
                echo "========================================"

                sh '''
                    git checkout -B ${BRANCH_NAME} origin/${BRANCH_NAME}
                '''

                sh '''
                    echo "========================================"
                    echo "Checked Out Branch:"
                    git branch --show-current

                    echo ""
                    echo "Commit:"
                    git rev-parse HEAD

                    echo "========================================"
                '''
            }
        }


        // =====================================================
        // 3. SonarQube Analysis
        // =====================================================

        stage('SonarQube Analysis') {

            steps {

                echo "========================================"
                echo "Starting SonarQube Analysis"
                echo "========================================"

                withSonarQubeEnv("${SONARQUBE_SERVER}") {

                    withCredentials([
                        string(
                            credentialsId: "${SONAR_TOKEN_CREDENTIAL}",
                            variable: 'SONAR_TOKEN'
                        )
                    ]) {

                        sh '''
                            mvn clean verify sonar:sonar \
                            -Dsonar.projectKey=devops-sonarqube-demo \
                            -Dsonar.projectName=devops-sonarqube-demo \
                            -Dsonar.token="$SONAR_TOKEN"
                        '''
                    }
                }

                echo "SonarQube analysis completed."
            }
        }


        // =====================================================
        // 4. Quality Gate
        // =====================================================

        stage('Quality Gate') {

            steps {

                echo "========================================"
                echo "Waiting for SonarQube Quality Gate"
                echo "========================================"

                timeout(time: 10, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }

                echo "========================================"
                echo "CODE QUALITY GATE PASSED"
                echo "========================================"
            }
        }


        // =====================================================
        // 5. Manual Approval
        // =====================================================

        stage('Approval') {

            steps {

                script {

                    def approval = input(
                        message: 'Do you approve the deployment?',
                        parameters: [
                            choice(
                                name: 'APPROVAL',
                                choices: 'APPROVE\nDENY',
                                description: 'Select APPROVE to continue or DENY to stop the pipeline'
                            )
                        ]
                    )

                    echo "Approval decision: ${approval}"

                    if (approval == 'APPROVE') {

                        echo "========================================"
                        echo "DEPLOYMENT APPROVED"
                        echo "========================================"

                    } else {

                        error("Deployment denied by approver. Pipeline stopped.")
                    }
                }
            }
        }


        // =====================================================
        // 6. Maven Build on Jenkins
        // =====================================================

        stage('Maven Build') {

            steps {

                echo "========================================"
                echo "Building Application on Jenkins"
                echo "========================================"

                sh '''
                    mvn clean package -DskipTests
                '''

                echo "========================================"
                echo "JENKINS MAVEN BUILD SUCCESSFUL"
                echo "========================================"
            }
        }


        // =====================================================
        // 7. Build + Push + Deploy on Application Server
        // =====================================================

        stage('Build and Deploy on Application Server') {

            steps {

                echo "========================================"
                echo "Application Server Deployment"
                echo "========================================"

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${EC2_SSH_CREDENTIALS}",
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USERNAME'
                    )
                ]) {

                    sh '''
                        set -e

                        echo "Application Server:"
                        echo "${EC2_HOST}"

                        echo ""
                        echo "Image:"
                        echo "${ECR_URI}:${IMAGE_TAG}"

                        echo ""
                        echo "Connecting to Application Server..."

                        ssh -o StrictHostKeyChecking=no \
                            -i "$SSH_KEY" \
                            "${SSH_USERNAME}@${EC2_HOST}" \
                            "AWS_REGION='${AWS_REGION}' \
                             ECR_URI='${ECR_URI}' \
                             ECR_REPOSITORY='${ECR_REPOSITORY}' \
                             IMAGE_TAG='${IMAGE_TAG}' \
                             APP_DIRECTORY='${APP_DIRECTORY}' \
                             BRANCH_NAME='${BRANCH_NAME}' \
                             bash -s" <<'REMOTE_SCRIPT'

                            set -e

                            echo "========================================"
                            echo "Application Server"
                            echo "========================================"

                            cd "$APP_DIRECTORY"

                            echo ""
                            echo "Pulling latest selected branch..."

                            git fetch origin

                            git checkout "$BRANCH_NAME" 2>/dev/null || true

                            git pull origin "$BRANCH_NAME"


                            echo ""
                            echo "========================================"
                            echo "Building Maven Application"
                            echo "========================================"

                            mvn clean package -DskipTests


                            echo ""
                            echo "========================================"
                            echo "Building Docker Image"
                            echo "========================================"

                            docker build \
                                -t "${ECR_REPOSITORY}:${IMAGE_TAG}" \
                                .


                            echo ""
                            echo "========================================"
                            echo "Logging into AWS ECR"
                            echo "========================================"

                            aws ecr get-login-password \
                                --region "$AWS_REGION" | \
                                docker login \
                                --username AWS \
                                --password-stdin "$ECR_URI"


                            echo ""
                            echo "========================================"
                            echo "Tagging Docker Image"
                            echo "========================================"

                            docker tag \
                                "${ECR_REPOSITORY}:${IMAGE_TAG}" \
                                "${ECR_URI}:${IMAGE_TAG}"


                            echo ""
                            echo "========================================"
                            echo "Pushing Docker Image to ECR"
                            echo "========================================"

                            docker push "${ECR_URI}:${IMAGE_TAG}"


                            echo ""
                            echo "========================================"
                            echo "Stopping Existing Container"
                            echo "========================================"

                            docker stop devops-app || true

                            docker rm devops-app || true


                            echo ""
                            echo "========================================"
                            echo "Pulling Image from ECR"
                            echo "========================================"

                            docker pull "${ECR_URI}:${IMAGE_TAG}"


                            echo ""
                            echo "========================================"
                            echo "Starting New Container"
                            echo "========================================"

                            docker run -d \
                                --name devops-app \
                                -p 8080:8080 \
                                "${ECR_URI}:${IMAGE_TAG}"


                            echo ""
                            echo "========================================"
                            echo "Deployment Completed"
                            echo "========================================"

                            echo ""
                            echo "Running Container:"
                            docker ps

                            echo ""
                            echo "Application Logs:"
                            docker logs --tail 30 devops-app

REMOTE_SCRIPT
                    '''
                }
            }
        }
    }


    // =========================================================
    // POST ACTIONS
    // =========================================================

    post {

        success {

            echo """
            ========================================
                    PIPELINE SUCCESS
            ========================================

            Branch:
            ${params.BRANCH_NAME}

            SonarQube:
            Analysis Completed

            Quality Gate:
            PASSED

            Approval:
            APPROVED

            Maven:
            BUILD SUCCESSFUL

            Docker:
            BUILT ON APPLICATION SERVER

            ECR:
            IMAGE PUSHED

            Application Server:
            ${EC2_HOST}

            Container:
            devops-app

            Image:
            ${ECR_URI}:${IMAGE_TAG}

            Application Port:
            8080

            ========================================
            """
        }


        failure {

            echo """
            ========================================
                    PIPELINE FAILED
            ========================================

            Branch:
            ${params.BRANCH_NAME}

            Application Server:
            ${EC2_HOST}

            Please check the Jenkins Console Output.

            ========================================
            """
        }


        aborted {

            echo """
            ========================================
                    PIPELINE ABORTED
            ========================================

            Branch:
            ${params.BRANCH_NAME}

            Pipeline was aborted by user or timeout.

            ========================================
            """
        }
    }
}
