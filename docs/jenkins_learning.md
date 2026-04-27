# JENKINS LEARNING GUIDE

## Table of Contents
1. [What is Jenkins?](#1-what-is-jenkins)
2. [Jenkins Architecture](#2-jenkins-architecture)
3. [Key Terminology](#3-key-terminology)
4. [Pipeline Types](#4-pipeline-types)
5. [Jenkinsfile Syntax](#5-jenkinsfile-syntax)
6. [Triggers & Build Types](#6-triggers--build-types)
7. [Credentials & Security](#7-credentials--security)
8. [Agents & Executors](#8-agents--executors)
9. [Practice Exercises](#9-practice-exercises)
10. [Interview Questions](#10-interview-questions)

---

## 1. What is Jenkins?

**Jenkins** is an open-source automation server used for:
- **Continuous Integration (CI)** - Automatically build and test code on every commit
- **Continuous Delivery (CD)** - Keep code ready for deployment at all times
- **Continuous Deployment** - Automatically deploy every change to production

### Key Features:
- 1800+ plugins available
- Written in Java
- Free and open-source (MIT License)
- Supports Git, GitHub, GitLab, Bitbucket
- Multi-platform (Windows, Linux, macOS)

---

## 2. Jenkins Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     JENKINS MASTER                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐   │
│  │ Job      │  │ Scheduler│  │ Web UI & REST API    │   │
│  │ Config   │  │          │  │                      │   │
│  └──────────┘  └──────────┘  └──────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │            Build Executors (slots)              │    │
│  │  [Exec 1] [Exec 2] [Exec 3] [Exec 4] [Exec 5]  │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌─────────┐    ┌─────────┐    ┌─────────┐
    │ AGENT 1 │    │ AGENT 2 │    │ AGENT 3 │
    │ (EC2)   │    │ (Docker)│    │ (K8s)   │
    └─────────┘    └─────────┘    └─────────┘
```

### Master
- Hosts Jenkins UI, manages jobs, schedules builds
- Stores configuration and build history
- Can run builds directly (not recommended)

### Agent
- Separate machine for running builds
- Offloads work from master
- Can be physical, virtual, or container

---

## 3. Key Terminology

| Term | Definition |
|------|------------|
| **Job/Project** | A task that Jenkins automates (freestyle, pipeline, etc.) |
| **Build** | Single execution of a job (has number, status, artifacts) |
| **Pipeline** | Workflow defined as code (Jenkinsfile) |
| **Stage** | Logical group of steps (Build, Test, Deploy) |
| **Step** | Single action (run shell, echo, sh) |
| **Agent** | Machine where pipeline runs |
| **Executor** | Slot on agent for running builds (parallel execution) |
| **Workspace** | Directory on agent where build runs |
| **Artifact** | Output files (JAR, WAR, Docker image) |
| **Upstream Job** | Job that triggers another job |
| **Downstream Job** | Job triggered by another job |
| **Build Number** | Sequential number for each build (Build #1, #2, #3) |
| **Artifact** | Files created by build (JAR, Docker image) |

---

## 4. Pipeline Types

### 4.1 Freestyle Project
- GUI-based, simple
- Good for quick tasks
- Limited flexibility

### 4.2 Scripted Pipeline (Groovy)
```groovy
node {
    stage('Build') {
        echo 'Building application...'
    }
    stage('Test') {
        echo 'Running tests...'
    }
}
```

### 4.3 Declarative Pipeline (Recommended)
```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                echo 'Building application...'
            }
        }
        stage('Test') {
            steps {
                echo 'Running tests...'
            }
        }
    }
}
```

**Why Declarative?**
- Structured, easier to read
- Built-in error handling
- Recommended by Jenkins
- Better visualization in Blue Ocean

---

## 5. Jenkinsfile Syntax

### Basic Structure
```groovy
pipeline {
    agent any  // Where to run

    stages {   // What to do
        stage('Build') {
            steps {
                // How to do it
            }
        }
    }

    post {     // What to do after
        always {
            cleanWs()
        }
    }
}
```

### Example: Full CI/CD Pipeline
```groovy
pipeline {
    agent any

    environment {
        APP_NAME = 'jira-ops-backend'
        REGION = 'ap-south-1'
        ECR_REPO = '123456789.dkr.ecr.ap-south-1.amazonaws.com/jira-ops-backend'
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

        stage('Docker Build & Push') {
            steps {
                echo 'Building Docker image...'
                sh '''
                    aws ecr get-login-password --region $REGION | \
                    docker login --username AWS --password-stdin $ECR_REPO
                    docker build -t $APP_NAME:$BUILD_NUMBER .
                    docker tag $APP_NAME:$BUILD_NUMBER $ECR_REPO:$BUILD_NUMBER
                    docker push $ECR_REPO:$BUILD_NUMBER
                '''
            }
        }

        stage('Deploy to EC2') {
            when { branch 'main' }
            steps {
                echo 'Deploying to EC2...'
                sshagent(credentials: ['ec2-ssh-key']) {
                    sh 'ssh -o StrictHostKeyChecking=no ec2-user@$EC2_HOST "docker pull $ECR_REPO:$BUILD_NUMBER && docker stop app || true && docker run -d --name app $ECR_REPO:$BUILD_NUMBER"'
                }
            }
        }
    }

    post {
        success {
            echo 'Build successful!'
        }
        failure {
            echo 'Build failed!'
        }
        always {
            cleanWs()
        }
    }
}
```

### Key Sections Explained

| Section | Purpose |
|---------|---------|
| `agent any` | Run on any available agent |
| `environment` | Define environment variables |
| `stages` | Group of stages |
| `stage` | Logical group (Build, Test, Deploy) |
| `steps` | Actual commands to execute |
| `post` | Actions after stages (success, failure, always) |
| `when` | Conditional execution (branch, expression) |

---

## 6. Triggers & Build Types

### Build Triggers

| Trigger | Description | Syntax |
|---------|-------------|--------|
| **Poll SCM** | Check for changes periodically | `H/5 * * * *` (every 5 min) |
| **Webhook** | Git notifies on push | GitHub webhook |
| **Cron** | Scheduled build | `0 2 * * *` (2 AM daily) |
| **Upstream** | Triggered by another job | Job dependency |
| **Manual** | Click "Build Now" | User triggered |
| **PR Trigger** | Pull request created | `changeRequest()` |

### Cron Syntax
```
┌───────────── minute (0 - 59)
│ ┌───────────── hour (0 - 23)
│ │ ┌───────────── day of month (1 - 31)
│ │ │ ┌───────────── month (1 - 12)
│ │ │ │ ┌───────────── day of week (0 - 7) (Sunday = 0 or 7)
│ │ │ │ │
* * * * *
```

### Examples:
- `H/5 * * * *` - Every 5 minutes
- `0 2 * * *` - Every day at 2 AM
- `0 */4 * * *` - Every 4 hours
- `H H(1-5) * * 1-5` - Weekdays during business hours

### Trigger Configuration in Pipeline:
```groovy
pipeline {
    triggers {
        githubPush()           // GitHub webhook
        pollSCM('H/5 * * * *') // Poll every 5 minutes
        cron('0 2 * * *')       // Daily at 2 AM
    }
    stages {
        stage('Build') {
            steps {
                echo 'Building...'
            }
        }
    }
}
```

---

## 7. Credentials & Security

### Adding Credentials:
1. Jenkins Dashboard → **Manage Jenkins** → **Credentials**
2. Click **Add Credentials**
3. Choose type and fill details
4. Credentials are stored securely

### Credential Types:

| Type | Use Case |
|------|----------|
| **Username with password** | GitHub, Docker Hub |
| **SSH Username with private key** | EC2 deployment |
| **Secret text** | API keys, tokens |
| **Secret file** | Certificate files |

### Using Credentials in Pipeline:

**Environment Variables:**
```groovy
pipeline {
    environment {
        DOCKER_CREDS = credentials('docker-hub-creds')
        AWS_ACCESS_KEY = credentials('aws-access-key')
    }

    stages {
        stage('Push Image') {
            steps {
                sh '''
                    echo $DOCKER_CREDS | docker login -u $USERNAME --password-stdin
                '''
            }
        }
    }
}
```

**SSH Agent:**
```groovy
pipeline {
    stages {
        stage('Deploy') {
            steps {
                sshagent(credentials: ['ec2-ssh-key']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no ec2-user@$EC2_HOST "docker pull myapp:latest"
                    '''
                }
            }
        }
    }
}
```

---

## 8. Agents & Executors

### Agent Configuration

```groovy
agent any              // Any available agent
agent none            // No agent (run on master, for post steps)
agent label 'docker'  // Specific label
agent kubernetes      // Run in Kubernetes pods
```

### Labels
Labels help target specific agents:
```groovy
agent {
    label 'docker && linux'
}
```

### Multiple Agents
```groovy
pipeline {
    stages {
        stage('Build') {
            agent { label 'docker' }
            steps {
                sh 'docker build -t myapp .'
            }
        }
        stage('Deploy') {
            agent { label 'ec2' }
            steps {
                sh './deploy.sh'
            }
        }
    }
}
```

### Executor Configuration
- Master executors: Usually set to 0 (don't run builds on master)
- Agent executors: Based on hardware (2-4 per agent)

---

## 9. Practice Exercises

### Exercise 1: First Freestyle Job
1. Create new Item → Freestyle project
2. Add build step: Execute shell
   ```bash
   echo "Hello from Jenkins!"
   date
   ```
3. Click "Build Now"
4. Check Console Output

### Exercise 2: First Pipeline
1. Create new Item → Pipeline
2. Add this script:
   ```groovy
   pipeline {
       agent any
       stages {
           stage('Hello') {
               steps {
                   echo 'Hello World!'
               }
           }
       }
   }
   ```
3. Run pipeline

### Exercise 3: Multi-Stage Pipeline
```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                echo 'Building...'
            }
        }
        stage('Test') {
            steps {
                echo 'Testing...'
            }
        }
        stage('Deploy') {
            steps {
                echo 'Deploying...'
            }
        }
    }
    post {
        always {
            echo 'Cleaning up...'
        }
    }
}
```

### Exercise 4: Conditional Deployment
```groovy
pipeline {
    agent any
    stages {
        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                echo 'Deploying to production...'
            }
        }
    }
}
```

---

## 10. Interview Questions

### Q1: What is the difference between CI, CD, and CD?

**Answer:**
- **CI (Continuous Integration)**: Automatically build and test on every commit. Developers merge code frequently, and automated builds/tests catch errors early.
- **CD (Continuous Delivery)**: Code is always in a deployable state. After CI, code is automatically deployed to staging. Manual approval required for production.
- **CD (Continuous Deployment)**: Every change that passes all stages automatically deploys to production without manual approval.

---

### Q2: What is the difference between Agent and Executor?

**Answer:**
- **Agent**: The machine where builds run. Can be master, EC2 instance, Docker container, or Kubernetes pod.
- **Executor**: A slot on the agent for executing a build. An agent can have multiple executors for parallel builds. If all executors are busy, new builds wait in queue.

---

### Q3: How do you secure credentials in Jenkins?

**Answer:**
1. Store credentials in **Jenkins Credentials Store** (not in code)
2. Use credential bindings in pipelines:
   - Username/password
   - SSH credentials
   - Secret text/file
3. Use `credentials()` method to access in pipeline
4. Never commit secrets in Jenkinsfile or repository
5. Enable **Encryption** at rest for credentials

---

### Q4: What is a Jenkinsfile?

**Answer:**
A text file containing the pipeline definition in Groovy DSL, stored in the version control system (Git). Key benefits:
- Pipeline as Code
- Version control for pipeline
- Code review before deployment
- Audit trail

---

### Q5: Difference between Scripted and Declarative Pipeline?

| Aspect | Scripted | Declarative |
|--------|----------|-------------|
| Syntax | Groovy-based | Structured DSL |
| Flexibility | High (full Groovy) | Limited (predefined structure) |
| Learning Curve | Steeper | Easier |
| Error Handling | Manual try-catch | Built-in post section |
| Visualization | Blue Ocean limited | Full Blue Ocean support |
| Validation | Runtime | Pre-validation |
| Recommended | Complex use cases | Most projects |

---

### Q6: How do you rollback a deployment?

**Answer:**
1. **Deploy previous Docker image**: Use previous build number
   ```bash
   docker pull myapp:previous-build-number
   ```
2. **Kubernetes rollback**:
   ```bash
   kubectl rollout undo deployment/myapp
   ```
3. **ECS rollback**:
   ```bash
   aws ecs update-service --service myapp --task-definition myapp:previous
   ```
4. **Blue-Green deployment**: Route traffic back to old environment

---

### Q7: What are the different types of Jenkins jobs?

**Answer:**
1. **Freestyle Project** - GUI-based, simple build jobs
2. **Pipeline** - Code-based workflows
3. **Multi-configuration Project** - Matrix jobs (multiple axes)
4. **Folder** - Organize jobs
5. **Workflow Job** - Older pipeline support
6. **External Job** - Track non-Jenkins jobs
7. **Multi-branch Pipeline** - Automatically create jobs for each branch

---

### Q8: How does Jenkins pipeline handle failures?

**Answer:**
1. **Post Section**:
   ```groovy
   post {
       failure { echo 'Build failed!' }
       success { echo 'Build succeeded!' }
       always { echo 'Run regardless' }
       unstable { echo 'Build unstable' }
   }
   ```
2. **Try-catch in Scripted Pipeline**:
   ```groovy
   try {
       sh './build.sh'
   } catch (Exception e) {
       echo "Error: ${e.message}"
       currentBuild.result = 'FAILURE'
   }
   ```
3. **Input for manual approval**:
   ```groovy
   input message: 'Deploy to production?', submitter: 'admin'
   ```

---

### Q9: What is Blue Ocean?

**Answer:**
Blue Ocean is Jenkins' modern UI/UX designed for pipelines:
- Visual pipeline editor
- Clear visualization of pipeline stages
- Detailed logs
- Personalization

---

### Q10: How do you trigger a Jenkins job from another job?

**Answer:**

**Method 1: Trigger downstream job**
```groovy
pipeline {
    stages {
        stage('Trigger') {
            steps {
                build job: 'other-job-name', parameters: [
                    string(name: 'BRANCH', value: 'main')
                ]
            }
        }
    }
}
```

**Method 2: Using Jenkins CLI**
```bash
jenkins-cli build job-name -p PARAM=value
```

**Method 3: Webhook/API**
- Trigger via REST API with authentication

---

## Next Steps

After understanding this guide:
1. Set up Jenkins locally or on cloud
2. Create your first pipeline
3. Integrate with GitHub
4. Add credentials for AWS/Docker
5. Deploy to EC2
6. Monitor and improve

---

## Resources

- [Jenkins Documentation](https://www.jenkins.io/doc/)
- [Pipeline Examples](https://www.jenkins.io/doc/pipeline/examples/)
- [Plugins Index](https://plugins.jenkins.io/)