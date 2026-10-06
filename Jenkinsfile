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

        // GitHub
        GIT_URL = 'https://github.com/ashokanroopa/devops-sonarqube-demo.git'

        // SonarQube
        SONARQUBE_SERVER = 'SonarQube-Server'

        // AWS
        AWS_REGION = 'ap-south-1'

        // ECR repository name
        ECR_REPOSITORY = 'devops-sonarqube-demo'

        // Docker image tag
        IMAGE_TAG = "v${BUILD_NUMBER}"

        // EC2 SSH Jenkins credential
        EC2_SSH_CREDENTIALS = 'ec2-ssh-key'

        // EC2 deployment server
        EC2_HOST = '<YOUR-EC2-PUBLIC-IP>'

        // EC2 user
        EC2_USER = 'ubuntu'
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

                    sh '''
                        mvn clean verify sonar:sonar \
                        -Dsonar.projectKey=devops-sonarqube-demo \
                        -Dsonar.projectName=devops-sonarqube-demo
                    '''
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
                        message: 'Do you approve the build?',
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
                        echo "BUILD APPROVED"
                        echo "Proceeding to Docker Build"
                        echo "========================================"

                    } else {

                        error("Build denied by approver. Pipeline stopped.")
                    }
                }
            }
        }


        // =====================================================
        // 6. Maven Build
        // =====================================================

        stage('Build') {

            steps {

                echo "========================================"
                echo "Starting Application Build"
                echo "========================================"

                sh '''
                    mvn clean package -DskipTests
                '''

                echo "========================================"
                echo "APPLICATION BUILD SUCCESSFUL"
                echo "========================================"
            }
        }


        // =====================================================
        // 7. Docker Build
        // =====================================================

        stage('Docker Build') {

            steps {

                echo "========================================"
                echo "Building Docker Image"
                echo "========================================"

                sh '''
                    docker build \
                    -t ${ECR_REPOSITORY}:${IMAGE_TAG} .
                '''

                echo "Docker image created:"
                echo "${ECR_REPOSITORY}:${IMAGE_TAG}"

                sh '''
                    docker images
                '''
            }
        }


        // =====================================================
        // 8. Login to AWS ECR
        // =====================================================

        stage('ECR Login') {

            steps {

                echo "========================================"
                echo "Logging into AWS ECR"
                echo "========================================"

                sh '''
                    ACCOUNT_ID=$(aws sts get-caller-identity \
                    --query Account \
                    --output text)

                    ECR_URI=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

                    echo "ECR Registry:"
                    echo "${ECR_URI}"

                    aws ecr get-login-password \
                    --region ${AWS_REGION} | \
                    docker login \
                    --username AWS \
                    --password-stdin ${ECR_URI}
                '''
            }
        }


        // =====================================================
        // 9. Tag Docker Image
        // =====================================================

        stage('Tag Image') {

            steps {

                echo "========================================"
                echo "Tagging Docker Image"
                echo "========================================"

                sh '''
                    ACCOUNT_ID=$(aws sts get-caller-identity \
                    --query Account \
                    --output text)

                    ECR_URI=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}

                    docker tag \
                    ${ECR_REPOSITORY}:${IMAGE_TAG} \
                    ${ECR_URI}:${IMAGE_TAG}

                    echo "Image tagged as:"
                    echo "${ECR_URI}:${IMAGE_TAG}"
                '''
            }
        }


        // =====================================================
        // 10. Push Image to ECR
        // =====================================================

        stage('Push Image to ECR') {

            steps {

                echo "========================================"
                echo "Pushing Docker Image to ECR"
                echo "========================================"

                sh '''
                    ACCOUNT_ID=$(aws sts get-caller-identity \
                    --query Account \
                    --output text)

                    ECR_URI=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}

                    docker push ${ECR_URI}:${IMAGE_TAG}
                '''

                echo "========================================"
                echo "DOCKER IMAGE PUSHED TO ECR"
                echo "========================================"
            }
        }


        // =====================================================
        // 11. Deploy to EC2
        // =====================================================

        stage('Deploy to EC2') {

            steps {

                echo "========================================"
                echo "Deploying Application to EC2"
                echo "========================================"

                sshagent(["${EC2_SSH_CREDENTIALS}"]) {

                    sh '''
                        ACCOUNT_ID=$(aws sts get-caller-identity \
                        --query Account \
                        --output text)

                        ECR_URI=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}

                        echo "Deploying image:"
                        echo "${ECR_URI}:${IMAGE_TAG}"

                        ssh -o StrictHostKeyChecking=no \
                        ${EC2_USER}@${EC2_HOST} "

                            echo 'Logging into ECR...'

                            aws ecr get-login-password \
                            --region ${AWS_REGION} | \
                            docker login \
                            --username AWS \
                            --password-stdin \
                            ${ECR_URI}

                            echo 'Pulling latest image...'

                            docker pull ${ECR_URI}:${IMAGE_TAG}

                            echo 'Stopping old container...'

                            docker stop devops-app || true

                            echo 'Removing old container...'

                            docker rm devops-app || true

                            echo 'Starting new container...'

                            docker run -d \
                            --name devops-app \
                            -p 8080:8080 \
                            ${ECR_URI}:${IMAGE_TAG}

                            echo 'Deployment completed.'

                            echo 'Running containers:'

                            docker ps
                        "
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
            Quality Gate PASSED

            Approval:
            APPROVED

            Docker:
            IMAGE BUILT

            ECR:
            IMAGE PUSHED

            EC2:
            DEPLOYMENT SUCCESSFUL

            Image Tag:
            ${IMAGE_TAG}

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
