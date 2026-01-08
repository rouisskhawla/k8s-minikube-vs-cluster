# Cluster Deployment

This folder contains all resources and instructions to deploy the application to a **Kubernetes cluster managed by Jenkins**. It includes:

* Kubernetes manifests (`deployment.yaml`, `service.yaml`)
* Jenkins pipeline setup
* Instructions for cluster setup and worker node joining
* Access information for the deployed application

> **Note:** In the Jenkins pipeline, `DEPLOY_TARGET` is automatically set to `cluster` when a Git **tag** (`vX.Y.Z`) is pushed. Deployment requires **manual approval**. Docker images are tagged using `scripts/docker-tag.sh` for consistent versioning.

---

## 1. Cluster Overview

**Architecture:**

```
GitHub
  │
  └─ Webhook → Jenkins (Manager VM)
        │
        ├─ Build Docker image
        ├─ Push image to Docker Hub
        └─ Deploy to Kubernetes Cluster
              │
              ├─ Control Plane (Manager VM)
              └─ Worker Node (Worker VM)
```

**Example IP Addresses (Bridged Network)**

| VM      | IP           | Role                    |
| ------- | ------------ | ----------------------- |
| Manager | 192.168.1.14 | Control Plane + Jenkins |
| Worker  | 192.168.1.11 | Worker Node             |

**Kubernetes Required Ports:** 6443, 2379-2380, 10250, 10257, 10259

---

## 2. Common Requirements (Both VMs)

### 2.1 Set Hostnames

```bash
# Manager VM
sudo hostnamectl set-hostname jenkins

# Worker VM
sudo hostnamectl set-hostname k8s-worker
```

> Reboot both VMs after updating hostnames.

### 2.2 Disable Swap

```bash
sudo swapoff -a
sudo sed -i '/ swap / s/^/#/' /etc/fstab
```

### 2.3 Configure Kernel Modules

```bash
sudo modprobe overlay
sudo modprobe br_netfilter

# Persist after reboot
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF
```

### 2.4 Configure Networking (sysctl)

```bash
cat <<EOF | sudo tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sudo sysctl --system
```

---

## 3. Installation

### 3.1 Install containerd (Both VMs)

```bash
sudo apt-get update
sudo apt-get install -y containerd

sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml

sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
sudo systemctl restart containerd
sudo systemctl enable containerd
```

### 3.2 Install Kubernetes Tools (Both VMs)

```bash
sudo mkdir -p /etc/apt/keyrings

curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.35/deb/Release.key \
 | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes.gpg

echo "deb [signed-by=/etc/apt/keyrings/kubernetes.gpg] https://pkgs.k8s.io/core:/stable:/v1.35/deb/ /" \
 | sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt-get update
sudo apt-get install -y kubelet kubeadm kubectl
sudo apt-mark hold kubelet kubeadm kubectl
```

---

## 4. Cluster Setup

### 4.1 Manager VM – Initialize Control Plane

```bash
sudo kubeadm init --apiserver-advertise-address=192.168.1.14
```

> Save the **`kubeadm join`** command for the worker VM.

Configure kubectl for Jenkins user:

```bash
mkdir -p /var/lib/jenkins/.kube
sudo cp /etc/kubernetes/admin.conf /var/lib/jenkins/.kube/config
sudo chown jenkins:jenkins /var/lib/jenkins/.kube/config
```

Verify cluster:

```bash
kubectl get nodes
kubectl get pods -n kube-system
```

Install Flannel CNI:

```bash
kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml
kubectl get pods -n kube-system
```

> Wait until all Flannel pods are `Running`.

### 4.2 Worker VM – Join Cluster

Run the **`kubeadm join`** command saved from the Manager VM:

```bash
sudo kubeadm join 192.168.1.14:6443 \
  --token <TOKEN> \
  --discovery-token-ca-cert-hash sha256:<HASH>
```

Verify from Manager VM:

```bash
kubectl get nodes
kubectl get pods -A
```

Expected:

* Manager node → `Ready`
* Worker node → `Ready`

---

## 5. Node IP Configuration & kube-proxy Fix

### 5.1 Manager VM

```bash
sudo tee /etc/default/kubelet <<EOF
KUBELET_EXTRA_ARGS=--node-ip=192.168.1.14
EOF

sudo systemctl daemon-reload
sudo systemctl restart kubelet
```

### 5.2 Worker VM

```bash
sudo tee /etc/default/kubelet <<EOF
KUBELET_EXTRA_ARGS=--node-ip=192.168.1.11
EOF

sudo systemctl daemon-reload
sudo systemctl restart kubelet
```

### 5.3 Recreate kube-proxy Pods

```bash
kubectl -n kube-system delete pod -l k8s-app=kube-proxy
```

Kubernetes will recreate the `kube-proxy` pods with the correct node IP.

---

## 6. Ubuntu 24.04 iptables-legacy Fix

* **Issue:** `kube-proxy` CrashLoopBackOff due to default **nftables**
* **Affected VMs:** Worker node (required), Manager node (recommended)

### 6.1 Worker Node (REQUIRED)

```bash
sudo apt update
sudo apt install -y iptables arptables ebtables

sudo update-alternatives --set iptables /usr/sbin/iptables-legacy
sudo update-alternatives --set ip6tables /usr/sbin/ip6tables-legacy
sudo update-alternatives --set arptables /usr/sbin/arptables-legacy
sudo update-alternatives --set ebtables /usr/sbin/ebtables-legacy

sudo systemctl restart containerd
sudo systemctl restart kubelet
```

### 6.2 Control-Plane Node (RECOMMENDED)

```bash
sudo apt update
sudo apt install -y iptables arptables ebtables

sudo update-alternatives --set iptables /usr/sbin/iptables-legacy
sudo update-alternatives --set ip6tables /usr/sbin/ip6tables-legacy
sudo update-alternatives --set arptables /usr/sbin/arptables-legacy
sudo update-alternatives --set ebtables /usr/sbin/ebtables-legacy

sudo systemctl restart containerd
sudo systemctl restart kubelet
```

### 6.3 Recreate kube-proxy Pods (Control-Plane Node ONLY)

```bash
kubectl -n kube-system delete pod -l k8s-app=kube-proxy
```

### 6.4 Verification (Any Node)

```bash
iptables --version
```

Expected:

```
iptables v1.x.x (legacy)
```

```bash
kubectl -n kube-system get pods -l k8s-app=kube-proxy
```

Expected:

```
STATUS: Running
```

---

## 7. Jenkins CI/CD Integration

* `DEPLOY_TARGET = cluster` for Git tags (`vX.Y.Z`)
* Deployment **requires manual approval** in Jenkins
* Jenkins applies:

```bash
kubectl apply -f cluster-deploy/deployment.yaml
kubectl apply -f cluster-deploy/service.yaml
```

* Verification:

  * Rollout status of deployment
  * Pod readiness and node placement
  * Service exposure via NodePort

---

## 8. Access Deployed Application

Retrieve the NodePort:

```bash
kubectl get svc k8s-cluster
```

Access using Worker Node IP and NodePort:

```
http://192.168.1.11:30082
```

---

## 9. References

* [Kubernetes Official Docs](https://kubernetes.io/docs/home/)
* [Flannel CNI Plugin](https://github.com/flannel-io/flannel)
