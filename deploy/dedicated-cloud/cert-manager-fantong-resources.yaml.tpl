apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod-fantong
spec:
  acme:
    email: __ACME_EMAIL__
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-prod-fantong-account-key
    solvers:
      - selector:
          dnsZones:
            - fantong.eu.org
        dns01:
          cloudflare:
            apiTokenSecretRef:
              name: cloudflare-api-token-fantong
              key: api-token
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: fantong-wildcard
  namespace: gateway-system
spec:
  secretName: fantong-wildcard-tls
  issuerRef:
    group: cert-manager.io
    kind: ClusterIssuer
    name: letsencrypt-prod-fantong
  dnsNames:
    - "*.fantong.eu.org"
