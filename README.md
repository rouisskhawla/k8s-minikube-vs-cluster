#  Multi-Environment CI/CD Pipeline: Minikube & Kubernetes Cluster


This project demonstrates a **CI/CD pipeline for a Spring Boot application** using **Jenkins, Docker, and Kubernetes**.
It supports **two deployment scenarios**—**local Minikube** for testing and development, and a **remote Kubernetes cluster** for production —using a **single Jenkinsfile**.

---

## Project Structure

```
.
├── minikube-deploy/        # Local deployment resources
│   ├── deployment.yaml
│   ├── screenshots/
│   └── README.md           # Minikube-specific setup & instructions
├── cluster-deploy/         # Remote cluster resources
│   ├── deployment.yaml
│   ├── screenshots/
│   └── README.md           # Cluster-specific setup & instructions
├── Jenkinsfile             # Shared CI/CD pipeline
└── README.md               # Root overview
```

**Notes:**

* Both folders contain **Kubernetes manifests** and **documentation** for their environment.
* The **Jenkinsfile** handles **build, Docker image push, and deployment**, but uses the appropriate manifest depending on the target environment.

---

## Deployment Approaches

### 1. Minikube

* Runs on **Jenkins VM** using Minikube.
* Triggered **automatically** on push to `main`.
* Suitable for **local development, testing, and CI validation**.
* Deployment state is documented in `minikube-deploy/screenshots/`.
* Detailed instructions are in [`minikube-deploy/README.md`](minikube-deploy/README.md).

### 2. Remote Kubernetes Cluster

* Runs on a **separate cluster** (VM or cloud).
* Triggered **manually** or conditionally in the pipeline.
* Simulates **staging/production environments**.
* Deployment state is documented in `cluster-deploy/screenshots/`.
* Detailed instructions are in [`cluster-deploy/README.md`](cluster-deploy/README.md).

---

## Jenkins Pipeline Overview

* **Jenkinsfile** orchestrates CI/CD for both deployments.
* Steps:

  1. Clone repository
  2. Run Maven build and tests
  3. Build and push Docker image
  4. Deploy to the target environment (Minikube or Remote Cluster)
  5. Verify deployment status

**Target selection** is determined by which deployment manifest is applied in the pipeline.

---

## Key Points

* Docker images are **shared** between both deployment targets.
* Each deployment folder contains **environment-specific resources**; root README provides high-level guidance.
* Screenshots and documentation in each folder help visualize the deployment state.
* Designed for easy expansion to **multi-stage pipelines**, automated rollbacks, or production-ready clusters.

---

## Execution Flow

```
Push to main
 → Jenkins Pipeline 
    → Maven Build & Tests
    → Docker Image Build & Push
    → Deployment Minikube 
    → Manually trigger Deployment to Remote Cluster
    → Deployment Verification
```

---

## GitHub Webhook via ngrok

To enable automatic pipeline triggers from GitHub:

1. Install ngrok on the Jenkins VM:

```bash
wget https://bin.equinox.io/c/4VmDzA7iaHb/ngrok-stable-linux-amd64.zip
unzip ngrok-stable-linux-amd64.zip
sudo mv ngrok /usr/local/bin/
rm ngrok-stable-linux-amd64.zip
```

2. Start an HTTP tunnel for Jenkins port:

```bash
ngrok http 8080
```

3. Copy the public URL from ngrok `https://ed3ede566ec9.ngrok-free.app`

4. Configure GitHub webhook in repository:

```
Payload URL: https://ed3ede566ec9.ngrok-free.app/github-webhook/
Content type: application/json
Trigger: Just the push event
```

5. Push to `main` → Jenkins pipeline triggers automatically.


---

