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
                    sh '''
                        # Test with SSH agent using credentials ID directly
                        sshagent(credentials: ['ec2-key']) {
                            ssh -o StrictHostKeyChecking=no -o BatchMode=yes ec2-user@${EC2_HOST} "echo SSH connected successfully"
                        }
                    '''
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