pipeline {
  agent any

  environment {
    DOCKER_REGISTRY = 'https://index.docker.io/v1/'
    REPO_NAME = 'khawlarouiss/k8s-minikube-vs-cluster'
    DOCKER_CREDENTIALS_ID = 'dockerlogin'
  }

  stages {

    stage('Parse Webhook Variables') {
      steps {
        script {
          if (env.ref?.startsWith('refs/tags/')) {
            env.TAG_NAME = env.ref.replaceAll('refs/tags/', '')
          } else if (env.ref_type == 'tag') {
            env.TAG_NAME = env.ref
          } else if (env.ref?.startsWith('refs/heads/')) {
            env.BRANCH_NAME = env.ref.replaceAll('refs/heads/', '')
          }
        }
      }
    }

    stage('Clone Github Repository') {
      steps {
        script {
          if (env.TAG_NAME) {
            checkout([
              $class: 'GitSCM',
              branches: [[name: "refs/tags/${env.TAG_NAME}"]],
              userRemoteConfigs: [[
                url: 'git@github.com:rouisskhawla/k8s-minikube-vs-cluster.git',
                credentialsId: 'github'
              ]]
            ])
          } else {
            checkout scm
          }
        }
      }
    }

    stage('Set Deployment Target') {
      steps {
        script {
          env.DEPLOY_TARGET = env.TAG_NAME?.startsWith('v') ? 'cluster' : 'minikube'
          echo "Deployment target: ${env.DEPLOY_TARGET}"
        }
      }
    }

    stage('Maven Test') {
      tools {
        maven 'Maven 3.9.11'
        jdk 'jdk17'
      }
      steps {
        sh 'mvn clean install -DskipTests'
        sh 'mvn test'
      }
    }

    stage('Maven Package') {
      tools {
        maven 'Maven 3.9.11'
        jdk 'jdk17'
      }
      steps {
        sh 'mvn clean package -DskipTests'
      }
    }

    stage('Docker Login') {
      steps {
        withCredentials([
          usernamePassword(
            credentialsId: DOCKER_CREDENTIALS_ID,
            usernameVariable: 'DOCKER_USERNAME',
            passwordVariable: 'DOCKER_PASSWORD'
          )
        ]) {
          sh 'echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin'
        }
      }
    }

    stage('Build & Push Docker Image') {
      steps {
        script {
          sh 'chmod +x scripts/docker-tag.sh'

          def imageTag = sh(
            script: """
              export TAG_NAME='${env.TAG_NAME ?: ''}'
              export BUILD_NUMBER='${env.BUILD_NUMBER}'
              ./scripts/docker-tag.sh
            """,
            returnStdout: true
          ).trim()

          echo "Docker image tag: ${imageTag}"

          docker.withRegistry(DOCKER_REGISTRY, DOCKER_CREDENTIALS_ID) {
            def img = docker.build("${REPO_NAME}:${imageTag}", "-f Dockerfile .")
            img.push()
            img.push('latest')
          }
        }
      }
    }

    stage('Deploy to Minikube') {
      when {
        expression { env.DEPLOY_TARGET == 'minikube' }
      }
      steps {
        sh 'minikube start --driver=docker || true'
        sh 'kubectl config use-context minikube'
        sh 'kubectl apply -f minikube-deploy/deployment.yaml'
        sh 'kubectl apply -f minikube-deploy/service.yaml'
      }
    }

    stage('Verify Minikube Deployment') {
      when {
        expression { env.DEPLOY_TARGET == 'minikube' }
      }
      steps {
        sh 'kubectl rollout status deployment/k8s-minikube --timeout=120s'
        sh 'kubectl get pods -o wide'
        sh 'kubectl get svc'
        sh 'minikube service k8s-minikube --url'
      }
    }

    stage('Approval for Cluster Deployment') {
      when {
        expression { env.DEPLOY_TARGET == 'cluster' }
      }
      steps {
        input message: 'Deploy to Kubernetes cluster?', ok: 'Deploy'
      }
    }

    stage('Deploy to Kubernetes Cluster') {
      when {
        expression { env.DEPLOY_TARGET == 'cluster' }
      }
      steps {
        sh 'kubectl config use-context kubernetes-admin@kubernetes'
        sh 'kubectl apply -f cluster-deploy/deployment.yaml'
        sh 'kubectl apply -f cluster-deploy/service.yaml'
      }
    }

    stage('Verify Kubernetes Cluster Deployment') {
      when {
        expression { env.DEPLOY_TARGET == 'cluster' }
      }
      steps {
        sh 'kubectl rollout status deployment/k8s-cluster --timeout=120s'
        sh 'kubectl get pods -o wide'
        sh 'kubectl get svc k8s-cluster'
      }
    }
  }

  post {
    always {
      cleanWs()
    }
  }
}
