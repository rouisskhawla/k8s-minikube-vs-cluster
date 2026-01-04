pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'https://index.docker.io/v1/'
        REPO_NAME = 'khawlarouiss/k8s-minikube-vs-cluster'
        DOCKER_CREDENTIALS_ID = 'dockerlogin'
    }

  stages {
    stage('Clone Github Repository') {
      steps {
        checkout([
          $class: 'GitSCM',
          branches: [[name: 'main']],
          userRemoteConfigs: [[
            url: 'git@github.com:rouisskhawla/k8s-minikube-vs-cluster.git',
            credentialsId: 'github'
          ]]
        ])
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
        script {
          withCredentials([
            usernamePassword
            (
              credentialsId: DOCKER_CREDENTIALS_ID,
              usernameVariable: 'DOCKER_USERNAME',
              passwordVariable: 'DOCKER_PASSWORD'
            )
          ])
            {
            sh """
                echo \$DOCKER_PASSWORD | docker login -u \$DOCKER_USERNAME --password-stdin
            """
            }
        }
      }
    }
    
    stage('Build and Push Image to Docker Registry') {
      steps {
        input 'Do you want to build and push the Docker image?'
        script {
          docker.withRegistry("${DOCKER_REGISTRY}", "${DOCKER_CREDENTIALS_ID}")
            {
              docker.build("${REPO_NAME}:latest", '-f Dockerfile .')
              docker.image("${REPO_NAME}:latest").push('latest')
            }
        }
      }
    }

    stage('Deploy to Minikube') {
      steps {
        input 'Do you want to deploy to Minikube?'
        script {
          sh 'minikube start --driver=docker || true'
          sh 'kubectl config use-context minikube'
          sh 'kubectl apply -f minikube-deploy/deployment.yaml'
        }
      }
    }

    stage('Verify Minikube Deployment') {
      steps {
        script {
          sh 'kubectl rollout status deployment/k8s-minikube-vs-cluster --timeout=120s'
          sh 'kubectl get pods -o wide'
        }
      }
    }

  }
  post {
    always {
        cleanWs()
    }
  }
}
