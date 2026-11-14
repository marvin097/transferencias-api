#!/usr/bin/env bash
# Configura en GitHub los ambientes dev, qa y prod como código (requiere GitHub CLI autenticado como dueño del repo).
#   - prod: aprobación obligatoria de <usuario-aprobador> y prohibida la auto-aprobación.
#   - prod: solo se puede desplegar desde la rama main.
#   - Cada ambiente recibe sus propias contraseñas aleatorias como secretos (nadie las ve ni quedan en el repo).
#
# Uso: ./scripts/configurar-github.sh <usuario-aprobador>
set -euo pipefail

APROBADOR="${1:?Uso: $0 <usuario-aprobador>}"
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
APROBADOR_ID=$(gh api "users/$APROBADOR" -q .id)
echo "Repositorio: $REPO | Aprobador de prod: $APROBADOR ($APROBADOR_ID)"

echo "-> Invitando a $APROBADOR como colaborador (debe aceptar la invitación)"
gh api -X PUT "repos/$REPO/collaborators/$APROBADOR" --silent || true

# Contraseña aleatoria que cumple la política de SQL Server (mayúscula, minúscula, número y símbolo).
generar() { echo "Aa1!$(openssl rand -hex 16)"; }

for AMB in dev qa prod; do
  echo "-> Ambiente $AMB"
  if [ "$AMB" = "prod" ]; then
    gh api -X PUT "repos/$REPO/environments/$AMB" --silent --input - <<JSON
{
  "prevent_self_review": true,
  "reviewers": [{ "type": "User", "id": $APROBADOR_ID }],
  "deployment_branch_policy": { "protected_branches": false, "custom_branch_policies": true }
}
JSON
    gh api -X POST "repos/$REPO/environments/$AMB/deployment-branch-policies" -f name=main --silent 2>/dev/null || true
  else
    gh api -X PUT "repos/$REPO/environments/$AMB" --silent
  fi

  for SECRETO in MSSQL_SA_PASSWORD APP_DB_PASSWORD CORE_PASSWORD; do
    gh secret set "$SECRETO" --env "$AMB" --repo "$REPO" --body "$(generar)"
  done
done

echo "Listo. Revise: https://github.com/$REPO/settings/environments"
