pipeline {

  agent any

  environment {
    DOCKER_REGISTRY = 'https://index.docker.io/v1/'
    REPO_NAME = 'khawlarouiss/k8s-minikube-vs-cluster'
    DOCKER_CREDENTIALS_ID = 'dockerlogin'
  }

  parameters {
    choice(
      name: 'DEPLOY_TARGET',
      choices: ['--select--', 'minikube', 'cluster'],
      description: 'Select deployment target'
    )
  }

  stages {

    stage('Validate Parameters') {
      steps {
        script {
          if (params.DEPLOY_TARGET == '--select--') {
            error('You must select a deployment target')
          }
        }
      }
    }

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
      when {
        expression { params.DEPLOY_TARGET == 'minikube' }
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
        expression { params.DEPLOY_TARGET == 'minikube' }
      }
      steps {
        sh 'kubectl rollout status deployment/k8s-minikube --timeout=120s'
        sh 'kubectl get pods -o wide'
        sh 'kubectl get svc'
        sh 'minikube service k8s-minikube --url'
      }
    }

    stage('Deploy to Kubernetes Cluster') {
      when {
        expression { params.DEPLOY_TARGET == 'cluster' }
      }
      steps {
        sh 'kubectl apply -f cluster-deploy/deployment.yaml'
        sh 'kubectl apply -f cluster-deploy/service.yaml'
      }
    }

    stage('Verify Kubernetes Cluster Deployment') {
      when {
        expression { params.DEPLOY_TARGET == 'cluster' }
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