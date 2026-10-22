apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod-omnnu
spec:
  acme:
    email: __ACME_EMAIL__
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-prod-omnnu-account-key
    solvers:
      - selector:
          dnsZones:
            - omnnu.xyz
        dns01:
          cloudflare:
            apiTokenSecretRef:
              name: cloudflare-api-token
              key: api-token
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: omnnu-wildcard
  namespace: gateway-system
spec:
  secretName: omnnu-wildcard-tls
  issuerRef:
    group: cert-manager.io
    kind: ClusterIssuer
    name: letsencrypt-prod-omnnu
  dnsNames:
    - omnnu.xyz
    - "*.omnnu.xyz"
