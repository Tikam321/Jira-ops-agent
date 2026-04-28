pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-21-openjdk-arm64'
        REGION = 'ap-south-1'
        ECR_REPO = '634105254197.dkr.ecr.ap-south-1.amazonaws.com/jira-ops-agent'
        EC2_HOST = 'ec2-13-201-97-38.ap-south-1.compute.amazonaws.com'
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out code...'
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo 'Building application...'
                sh './gradlew clean build -x test'
            }
        }

        stage('Build & Push to ECR with Jib') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'awscreds',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {
                    echo 'Building Docker image with Jib and pushing to ECR...'
                    sh './gradlew jib --image=${ECR_REPO}:${BUILD_NUMBER}'
                }
            }
        }

        stage('Deploy to EC2') {
            steps {
                echo 'Deploying to EC2...'
                withCredentials([
                    usernamePassword(
                        credentialsId: 'awscreds',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                    ),
                    string(credentialsId: 'jira-client-id', variable: 'JIRA_CLIENT_ID'),
                    string(credentialsId: 'jira-client-secret', variable: 'JIRA_CLIENT_SECRET'),
                    string(credentialsId: 'groq-api-key', variable: 'GROQ_API_KEY'),
                    string(credentialsId: 'db-url', variable: 'DB_URL'),
                    string(credentialsId: 'db-user', variable: 'DB_USER'),
                    string(credentialsId: 'db-pass', variable: 'DB_PASS'),
                    string(credentialsId: 'frontend-url', variable: 'FRONTEND_URL')
                ]) {
                    sshagent(credentials: ['ec2-key']) {
                        sh '''
                            # Login to ECR first on local Jenkins
                            aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 634105254197.dkr.ecr.ap-south-1.amazonaws.com

                            # Pull image to local
                            docker pull 634105254197.dkr.ecr.ap-south-1.amazonaws.com/jira-ops-agent:'${BUILD_NUMBER}'

                            # SSH to EC2 with all variables passed explicitly
                            ssh -o StrictHostKeyChecking=no ec2-user@ec2-13-201-97-38.ap-south-1.compute.amazonaws.com << 'ENDSSH'
                                export REGION="ap-south-1"
                                export ECR_REPO="634105254197.dkr.ecr.ap-south-1.amazonaws.com/jira-ops-agent"
                                export BUILD_NUMBER="'"${BUILD_NUMBER}"'"
                                export JIRA_CLIENT_ID="'"${JIRA_CLIENT_ID}"'"
                                export JIRA_CLIENT_SECRET="'"${JIRA_CLIENT_SECRET}"'"
                                export GROQ_API_KEY="'"${GROQ_API_KEY}"'"
                                export DB_URL="'"${DB_URL}"'"
                                export DB_USER="'"${DB_USER}"'"
                                export DB_PASS="'"${DB_PASS}"'"
                                export FRONTEND_URL="'"${FRONTEND_URL}"'"

                                # Login to ECR on remote
                                aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_REPO}

                                # Stop and remove old container
                                docker stop jira-ops-agent || true
                                docker rm jira-ops-agent || true

                                # Pull and run new container
                                docker pull ${ECR_REPO}:${BUILD_NUMBER}
                                docker run -d --name jira-ops-agent -p 8080:8081 \
                                    -e SPRING_PROFILES_ACTIVE=prod \
                                    -e JIRA_OAUTH_CLIENT_ID=${JIRA_CLIENT_ID} \
                                    -e JIRA_OAUTH_CLIENT_SECRET=${JIRA_CLIENT_SECRET} \
                                    -e GROQ_API_KEY=${GROQ_API_KEY} \
                                    -e SPRING_DATASOURCE_URL=${DB_URL} \
                                    -e SPRING_DATASOURCE_USERNAME=${DB_USER} \
                                    -e SPRING_DATASOURCE_PASSWORD=${DB_PASS} \
                                    -e FRONTEND_URL=${FRONTEND_URL} \
                                    ${ECR_REPO}:${BUILD_NUMBER}
                            ENDSSH

                            echo "Deployment complete!"
                        '''
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Build and deployment successful!'
        }
        failure {
            echo 'Build or deployment failed!'
        }
        always {
            echo 'Cleaning up workspace...'
            deleteDir()
        }
    }
}