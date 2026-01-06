# Multi-Environment CI/CD Pipeline: Minikube & Kubernetes Cluster

This project demonstrates a **CI/CD pipeline for a Spring Boot application** using **Jenkins, Docker, and Kubernetes**.
It supports **two deployment scenarios**—**local Minikube** for testing and development, and a **remote Kubernetes cluster** for production—using a **Jenkinsfile** with **automatic target selection**.

---

## Project Structure

```
.
├── minikube-deploy/        # Local deployment resources
│   ├── deployment.yaml
│   ├── docs/
│   └── README.md           # Minikube-specific setup & instructions
├── cluster-deploy/         # Remote cluster resources
│   ├── deployment.yaml
│   ├── docs/
│   └── README.md           # Cluster-specific setup & instructions
├── Jenkinsfile             # Shared CI/CD pipeline
├── scripts/                # CI/CD scripts
└── README.md               # Root overview
```

**Notes:**

* Both folders contain **Kubernetes manifests** and **documentation** for their environment.
* The **Jenkinsfile** handles **build, Docker image push, and deployment**, selecting the appropriate environment automatically.
* Deployment target is determined by **GitHub context**:

  * **Push to `main`** → Minikube
  * **Push tag `vX.Y.Z`** → Kubernetes cluster (production release)

---

## Deployment Approaches

### 1. Minikube

* Triggered **automatically** on push to `main`.
* Runs on **Jenkins VM** using Minikube.
* Suitable for **local development, testing, and CI validation**.
* Deployment state is documented in `minikube-deploy/docs/`.
* Detailed instructions are in [`minikube-deploy/README.md`](minikube-deploy/README.md).

### 2. Remote Kubernetes Cluster

* Triggered **automatically** when a **Git tag** matching `vX.Y.Z` is pushed.
* Runs on a **separate cluster** (VM).
* Simulates **production environment**.
* Requires **manual approval in Jenkins** before deployment (production safety).
* Deployment state is documented in `cluster-deploy/docs/`.
* Detailed instructions are in [`cluster-deploy/README.md`](cluster-deploy/README.md).

---

## Jenkins Pipeline Overview

* **Jenkinsfile** orchestrates CI/CD for both environments.
* Deployment target is determined **automatically**:

```groovy
// Determine deployment target
DEPLOY_TARGET = env.TAG_NAME?.startsWith('v') ? 'cluster' : 'minikube'
```

* **Minikube** → commit on `main` without a tag
* **Cluster** → Git tag starting with `v` (production release)

---

## Jenkins Pipeline Configuration (Multibranch)

This project is configured as a **Jenkins Multibranch Pipeline** to support **branch-based** and **tag-based** executions using `Jenkinsfile`.

### Job Type

* **Jenkins job:** Multibranch Pipeline
* **Pipeline definition:** `Jenkinsfile` from SCM
* **Repository:** GitHub

---

### Required Multibranch Settings

In Jenkins, configure the Multibranch Pipeline with the following options:

#### Branch Sources → Git

* **Repository URL**

  ```
  git@github.com:rouisskhawla/k8s-minikube-vs-cluster.git
  ```
* **Credentials:** GitHub SSH key

#### Behaviours

Enable:

* **Discover branches**
* **Discover tags**

This allows Jenkins to automatically create pipelines for:

* `main` branch
* Git tags

---

### Build Configuration

* **Mode:** by Jenkinsfile
* **Script Path:** `Jenkinsfile`

---

### Pipeline Trigger Behavior

* **Push to `main`**

  * Triggers CI + **Minikube deployment**
* **Push Git tag `vX.Y.Z`**

  * Triggers **production pipeline**
  * Requires **manual approval**
  * Deploys to **Kubernetes cluster**

The deployment target is resolved automatically inside the Jenkinsfile using Git context.

---

### Jenkins Environment Variables Used

| Variable       | Purpose                                     |
| -------------- | ------------------------------------------- |
| `BRANCH_NAME`  | Identifies branch or tag name               |
| `TAG_NAME`     | Set only when the build is triggered by tag |
| `BUILD_NUMBER` | Used for snapshot image versioning          |

---

### Deployment Target in Jenkinsfile

To ensure **only the relevant stages execute**, add the following **`when` expressions** to each environment-specific stage:

```groovy
// For Minikube deployment stages
when {
    expression { env.DEPLOY_TARGET == 'minikube' }
}

// For Kubernetes Cluster deployment stages
when {
    expression { env.DEPLOY_TARGET == 'cluster' }
}
```

## Docker Image Versioning

The Docker image tag depends on whether the build is for a **release** or a **snapshot**:

### 1. Production Release

* Triggered by Git tag `vX.Y.Z`
* Docker image tag = `X.Y.Z`

Example:

| Git Event         | IMAGE_TAG |
| ----------------- | --------- |
| Tag push `v0.1.0` | `0.1.0`   |

### 2. Snapshot Build

* Triggered by a commit on `main` **without a tag**
* Docker image tag = `LATEST_TAG-BUILD_NUMBER`

  * `LATEST_TAG` = last Git tag (or `0.0.0` if no tags exist)
  * `BUILD_NUMBER` = incremental Jenkins build number

Examples:

| Latest Tag | Jenkins BUILD_NUMBER | IMAGE_TAG |
| ---------- | -------------------- | --------- |
| 0.0.1      | 45                   | 0.0.1-45  |
| 0.0.1      | 46                   | 0.0.1-46  |
| 0.0.2      | 50                   | 0.0.2-50  |
| (no tags)  | 1                    | 0.0.0-1   |

## Versioning Script

* **File:** [scripts/docker-tag.sh](scripts/docker-tag.sh)  
* **Purpose:** Calculates the Docker image tag based on Git tag or Jenkins build number for snapshot builds.
* **Make the script executable:**

```bash
chmod +x scripts/docker-tag.sh
```

* **Usage in Jenkinsfile:**

```groovy
// Run the script to get the Docker image tag
def imageTag = sh(script: "./scripts/calculate-docker-tag.sh", returnStdout: true).trim()

// Use the tag to build and push the Docker image
docker.build("${REPO_NAME}:${imageTag}", "-f Dockerfile .")
```

* This approach ensures **unique snapshot versions** per build and **production-safe release tags**.

---

## Execution Flow

```
Push to main (no tag)
 → Jenkins Pipeline 
    → Maven Build & Tests
    → Docker Image Build & Push
    → Deployment Minikube 
    → Deployment Verification

Push Git tag vX.Y.Z
 → Jenkins Pipeline
    → Maven Build & Tests
    → Docker Image Build & Push
    → Manual Approval in Jenkins
    → Deployment to Kubernetes Cluster
    → Deployment Verification
```

---

## Key Points

* Docker images are **shared** between both deployment targets.
* Each deployment folder contains **environment-specific resources**; root README provides high-level guidance.
* Screenshots and documentation in each folder help visualize the deployment state.
* Designed for easy expansion to **multi-stage pipelines**, automated rollbacks, or production-ready clusters.
* **Manual approval** ensures production safety for cluster deployments.

---

## GitHub Webhook via ngrok

To enable automatic pipeline triggers from GitHub:

1. **Install ngrok on the Jenkins VM**:

```bash
curl -sSL https://ngrok-agent.s3.amazonaws.com/ngrok.asc \
  | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc >/dev/null \
  && echo "deb https://ngrok-agent.s3.amazonaws.com bookworm main" \
  | sudo tee /etc/apt/sources.list.d/ngrok.list \
  && sudo apt update \
  && sudo apt install ngrok
```

2. **Add ngrok authentication token**:

```bash
ngrok config add-authtoken <YOUR_NGROK_TOKEN>
```

3. **Start an HTTP tunnel for Jenkins** :

```bash
ngrok http 8080
```

4. **Copy the public URL** provided by ngrok
   (e.g., `https://ed3ede566ec9.ngrok-free.app`).

5. **Configure GitHub webhook** in GitHub repository:

```
Payload URL: https://<ngrok-public-url>/github-webhook/
Content type: application/json
Trigger: Just the push event
```

6. **Triggering deployments**:

* Push to `main` → Jenkins pipeline triggers **Minikube deployment** automatically.
* Push a Git tag `vX.Y.Z` → Jenkins pipeline triggers **Kubernetes cluster deployment** with **manual approval**.

