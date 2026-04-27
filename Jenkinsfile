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
                    string(credentialsId: 'db-creds', variable: 'DB_CREDS')
                ]) {
                    sshagent(credentials: ['ec2ssh']) {
                        sh '''
                            # DB creds format: username:password@host:port/dbname
                            # Example: jira_ops_user:password123@db.host.com:5432/jira_ops
                            DB_USER=$(echo "$DB_CREDS" | cut -d: -f1)
                            DB_PASSWORD=$(echo "$DB_CREDS" | cut -d@ -f1 | cut -d: -f2)
                            DB_HOST_PORT=$(echo "$DB_CREDS" | cut -d@ -f2)
                            DB_NAME=$(echo "$DB_HOST_PORT" | cut -d/ -f2)
                            DB_HOST=$(echo "$DB_HOST_PORT" | cut -d: -f1)

                            aws ecr get-login-password --region ${REGION} | \
                            docker login --username AWS --password-stdin ${ECR_REPO}

                            ssh -o StrictHostKeyChecking=no ec2-user@${EC2_HOST} << EOF
                                docker stop jira-ops-agent || true
                                docker rm jira-ops-agent || true
                                docker pull ${ECR_REPO}:${BUILD_NUMBER}
                                docker run -d \
                                    --name jira-ops-agent \
                                    -p 8080:8081 \
                                    -e SPRING_PROFILES_ACTIVE=prod \
                                    -e JIRA_OAUTH_CLIENT_ID=${JIRA_CLIENT_ID} \
                                    -e JIRA_OAUTH_CLIENT_SECRET=${JIRA_CLIENT_SECRET} \
                                    -e GROQ_API_KEY=${GROQ_API_KEY} \
                                    -e SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME} \
                                    -e SPRING_DATASOURCE_USERNAME=${DB_USER} \
                                    -e SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD} \
                                    ${ECR_REPO}:${BUILD_NUMBER}
                            EOF
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