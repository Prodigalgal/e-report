#!/usr/bin/env bash
set -euo pipefail

KUBERNETES_MINOR="${KUBERNETES_MINOR:-v1.35}"
KUBERNETES_VERSION="${KUBERNETES_VERSION:-v1.35.6}"
KUBERNETES_PACKAGE_VERSION="${KUBERNETES_PACKAGE_VERSION:-1.35.6-1.1}"
KUBERNETES_IMAGE_REPOSITORY="${KUBERNETES_IMAGE_REPOSITORY:-registry.aliyuncs.com/google_containers}"
POD_CIDR="${POD_CIDR:-10.244.0.0/16}"
SERVICE_CIDR="${SERVICE_CIDR:-10.96.0.0/12}"
CILIUM_VERSION="${CILIUM_VERSION:-1.20.0}"
HELM_VERSION="${HELM_VERSION:-v4.2.3}"
RESET_INCOMPLETE_CLUSTER="${RESET_INCOMPLETE_CLUSTER:-false}"
ADVERTISE_ADDRESS="${ADVERTISE_ADDRESS:-$(ip -4 route get 1.1.1.1 | awk '{for (i=1; i<=NF; i++) if ($i == "src") {print $(i+1); exit}}')}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y apt-transport-https ca-certificates curl gpg containerd

swapoff -a
if grep -Eq '^[^#].+[[:space:]]swap[[:space:]]' /etc/fstab; then
  cp -a /etc/fstab "/etc/fstab.pre-kubernetes.$(date +%Y%m%d%H%M%S)"
  sed -ri '/^[^#].+[[:space:]]swap[[:space:]]/s/^/# kubernetes-disabled /' /etc/fstab
fi

cat >/etc/modules-load.d/kubernetes.conf <<'EOF'
overlay
br_netfilter
EOF
modprobe overlay
modprobe br_netfilter

cat >/etc/sysctl.d/99-kubernetes.conf <<'EOF'
net.bridge.bridge-nf-call-iptables = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward = 1
EOF
sysctl --system >/dev/null

mkdir -p /etc/containerd
containerd config default >/etc/containerd/config.toml
sed -ri 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
sed -ri "s#sandbox_image = \"[^\"]+\"#sandbox_image = \"${KUBERNETES_IMAGE_REPOSITORY}/pause:3.10.1\"#" /etc/containerd/config.toml
sed -ri "s#sandbox = '[^']+'#sandbox = '${KUBERNETES_IMAGE_REPOSITORY}/pause:3.10.1'#" /etc/containerd/config.toml
systemctl enable containerd
systemctl restart containerd

install -d -m 0755 /etc/apt/keyrings
curl -fsSL "https://pkgs.k8s.io/core:/stable:/${KUBERNETES_MINOR}/deb/Release.key" \
  | gpg --dearmor --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
cat >/etc/apt/sources.list.d/kubernetes.list <<EOF
deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/${KUBERNETES_MINOR}/deb/ /
EOF
apt-get update
apt-mark unhold kubelet kubeadm kubectl >/dev/null 2>&1 || true
apt-get install -y --allow-downgrades \
  "kubelet=${KUBERNETES_PACKAGE_VERSION}" \
  "kubeadm=${KUBERNETES_PACKAGE_VERSION}" \
  "kubectl=${KUBERNETES_PACKAGE_VERSION}"
apt-mark hold kubelet kubeadm kubectl
systemctl enable kubelet

if ! command -v helm >/dev/null 2>&1; then
  workdir="$(mktemp -d)"
  trap 'rm -rf "${workdir}"' EXIT
  curl -fsSLo "${workdir}/helm.tar.gz" "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz"
  curl -fsSLo "${workdir}/helm.tar.gz.sha256sum" "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz.sha256sum"
  expected_sha256="$(awk '{print $1}' "${workdir}/helm.tar.gz.sha256sum")"
  echo "${expected_sha256}  ${workdir}/helm.tar.gz" | sha256sum -c -
  tar -xzf "${workdir}/helm.tar.gz" -C "${workdir}"
  install -m 0755 "${workdir}/linux-amd64/helm" /usr/local/bin/helm
fi

if [[ -f /etc/kubernetes/admin.conf ]] && ! kubectl --kubeconfig /etc/kubernetes/admin.conf get --raw=/readyz >/dev/null 2>&1; then
  if [[ "${RESET_INCOMPLETE_CLUSTER}" != "true" ]]; then
    echo "An incomplete Kubernetes control plane exists. Re-run with RESET_INCOMPLETE_CLUSTER=true after inspection." >&2
    exit 1
  fi
  kubeadm reset --force --cri-socket unix:///run/containerd/containerd.sock
  rm -rf /etc/cni/net.d /root/.kube/config
fi

if [[ ! -f /etc/kubernetes/admin.conf ]]; then
  kubeadm_config="$(mktemp)"
  cat >"${kubeadm_config}" <<EOF
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${ADVERTISE_ADDRESS}
  bindPort: 6443
nodeRegistration:
  criSocket: unix:///run/containerd/containerd.sock
  imagePullPolicy: IfNotPresent
---
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
kubernetesVersion: ${KUBERNETES_VERSION}
controlPlaneEndpoint: ${ADVERTISE_ADDRESS}:6443
imageRepository: ${KUBERNETES_IMAGE_REPOSITORY}
networking:
  dnsDomain: cluster.local
  podSubnet: ${POD_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
EOF
  kubeadm config images pull --config "${kubeadm_config}"
  kubeadm init --config "${kubeadm_config}"
  rm -f "${kubeadm_config}"
fi

install -d -m 0700 /root/.kube
install -m 0600 /etc/kubernetes/admin.conf /root/.kube/config
export KUBECONFIG=/etc/kubernetes/admin.conf

helm upgrade --install cilium oci://quay.io/cilium/charts/cilium \
  --version "${CILIUM_VERSION}" \
  --namespace kube-system \
  --set ipam.mode=kubernetes \
  --set kubeProxyReplacement=false \
  --set operator.replicas=1

kubectl -n kube-system rollout status daemonset/cilium --timeout=10m
kubectl -n kube-system rollout status deployment/cilium-operator --timeout=10m
kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
kubectl label nodes --all node.kubernetes.io/exclude-from-external-load-balancers- 2>/dev/null || true
kubectl wait --for=condition=Ready node --all --timeout=10m

kubectl get nodes -o wide
kubectl get pods -A
