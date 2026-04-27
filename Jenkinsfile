pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-21-openjdk-arm64'
        REGION = 'ap-south-1'
        ECR_REPO = '123456789.dkr.ecr.ap-south-1.amazonaws.com/jira-ops-agent'
        EC2_HOST = 'ec2-host.compute.amazonaws.com'
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

        stage('Test') {
            steps {
                echo 'Running tests...'
                sh './gradlew test'
            }
        }

        stage('Build & Push to ECR with Jib') {
            steps {
                echo 'Building Docker image with Jib and pushing to ECR...'
                sh './gradlew jib --image=${ECR_REPO}:${BUILD_NUMBER}'
            }
        }

        stage('Deploy to EC2') {
            when {
                branch 'main'
            }
            steps {
                echo 'Deploying to EC2...'
                sshagent(credentials: ['ec2ssh']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no ec2-user@${EC2_HOST} << EOF
                            aws ecr get-login-password --region ${REGION} | \
                            docker login --username AWS --password-stdin ${ECR_REPO}

                            docker stop jira-ops-agent || true
                            docker rm jira-ops-agent || true
                            docker pull ${ECR_REPO}:${BUILD_NUMBER}
                            docker run -d \
                                --name jira-ops-agent \
                                -p 8080:8081 \
                                -e SPRING_PROFILES_ACTIVE=prod \
                                ${ECR_REPO}:${BUILD_NUMBER}
                        EOF
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
            cleanWs()
        }
    }
}