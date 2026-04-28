pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-21-openjdk-arm64'
        REGION = 'ap-south-1'
        ECR_REPO = '634105254197.dkr.ecr.ap-south-1.amazonaws.com/jira-ops-agent'
        EC2_HOST = 'ec2-13-201-97-38.ap-south-1.compute.amazonaws.com'
        CONTAINER_NAME = 'jira-ops-agent'
        CONTAINER_PORT = '8081'
        HOST_PORT = '8080'
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
                        sh """
                            set -e

                            echo "=== Deploying to EC2: ${EC2_HOST} ==="
                            echo "Image: ${ECR_REPO}:${BUILD_NUMBER}"

                            # Create deployment script
                            cat > /tmp/deploy.sh << 'DEPLOY_SCRIPT'
#!/bin/bash
set -e

# Configure AWS credentials
mkdir -p ~/.aws
cat > ~/.aws/credentials << 'AWS_CREDS'
[default]
aws_access_key_id = \${AWS_ACCESS_KEY_ID}
aws_secret_access_key = \${AWS_SECRET_ACCESS_KEY}
AWS_CREDS

cat > ~/.aws/config << 'AWS_CONFIG'
[default]
region = \${REGION}
output = json
AWS_CONFIG

# Login to ECR
echo "Logging into ECR..."
aws ecr get-login-password --region \${REGION} | docker login --username AWS --password-stdin \${ECR_REPO}

# Stop and remove old container
echo "Stopping old container..."
docker stop \${CONTAINER_NAME} || true
docker rm \${CONTAINER_NAME} || true

# Pull new image
echo "Pulling new image: \${ECR_REPO}:\${BUILD_NUMBER}"
docker pull \${ECR_REPO}:\${BUILD_NUMBER}

# Run new container
echo "Starting new container..."
docker run -d --name \${CONTAINER_NAME} \\
    -p \${HOST_PORT}:\${CONTAINER_PORT} \\
    -e SPRING_PROFILES_ACTIVE=prod \\
    -e JIRA_OAUTH_CLIENT_ID=\${JIRA_CLIENT_ID} \\
    -e JIRA_OAUTH_CLIENT_SECRET=\${JIRA_CLIENT_SECRET} \\
    -e GROQ_API_KEY=\${GROQ_API_KEY} \\
    -e SPRING_DATASOURCE_URL=\${DB_URL} \\
    -e SPRING_DATASOURCE_USERNAME=\${DB_USER} \\
    -e SPRING_DATASOURCE_PASSWORD=\${DB_PASS} \\
    -e FRONTEND_URL=\${FRONTEND_URL} \\
    \${ECR_REPO}:\${BUILD_NUMBER}

# Clean up old images (keep last 5)
echo "Cleaning up old images..."
docker images \${ECR_REPO} --format '{{.Tag}}' | tail -n +6 | xargs -I {} docker rmi \${ECR_REPO}:{} || true

# Clean up AWS credentials
rm -f ~/.aws/credentials ~/.aws/config

echo "Deployment complete!"
DEPLOY_SCRIPT

                            # Copy and execute deployment script
                            scp -o StrictHostKeyChecking=no /tmp/deploy.sh ec2-user@${EC2_HOST}:/tmp/
                            ssh -o StrictHostKeyChecking=no ec2-user@${EC2_HOST} \\
                                AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \\
                                AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \\
                                REGION="${REGION}" \\
                                ECR_REPO="${ECR_REPO}" \\
                                BUILD_NUMBER="${BUILD_NUMBER}" \\
                                CONTAINER_NAME="${CONTAINER_NAME}" \\
                                CONTAINER_PORT="${CONTAINER_PORT}" \\
                                HOST_PORT="${HOST_PORT}" \\
                                JIRA_CLIENT_ID="${JIRA_CLIENT_ID}" \\
                                JIRA_CLIENT_SECRET="${JIRA_CLIENT_SECRET}" \\
                                GROQ_API_KEY="${GROQ_API_KEY}" \\
                                DB_URL="${DB_URL}" \\
                                DB_USER="${DB_USER}" \\
                                DB_PASS="${DB_PASS}" \\
                                FRONTEND_URL="${FRONTEND_URL}" \\
                                'bash /tmp/deploy.sh'

                            echo "=== Deployment successful! ==="
                        """
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                echo 'Performing health check...'
                sh """
                    set +e
                    MAX_ATTEMPTS=30
                    ATTEMPT=0
                    HEALTH_URL="http://${EC2_HOST}:${HOST_PORT}/actuator/health"

                    echo "Checking health at: \${HEALTH_URL}"

                    while [ \${ATTEMPT} -lt \${MAX_ATTEMPTS} ]; do
                        ATTEMPT=\$((ATTEMPT + 1))
                        echo "Attempt \${ATTEMPT}/\${MAX_ATTEMPTS}..."

                        RESPONSE=\$(curl -s -o /dev/null -w "%{http_code}" \${HEALTH_URL} || echo "000")

                        if [ "\${RESPONSE}" = "200" ]; then
                            echo "Health check passed!"
                            exit 0
                        fi

                        echo "Status: \${RESPONSE} - Waiting..."
                        sleep 5
                    done

                    echo "Health check failed after \${MAX_ATTEMPTS} attempts"
                    exit 1
                """
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