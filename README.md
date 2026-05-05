# Guia de Instalação PJT (Modo Deus)

Este guia explica como instalar o sistema **PJT** e ativar o controle total (**Device Owner**) no tablet.

## 📋 Pré-requisitos no Tablet
Para que o Modo Deus seja ativado, o tablet deve estar em um estado "limpo" de contas:
1. Vá em **Configurações > Contas e Backup > Gerenciar Contas**.
2. **Remova todas as contas** (Google, Samsung, Outlook, etc.). Você pode adicioná-las de volta depois.
3. Ative as **Opções do Desenvolvedor** (clique 7 vezes no Número da Versão em Sobre o Tablet).
4. Ative a **Depuração USB**.

## 🚀 Como instalar

### Opção A: Usando o Script Automatizado (Recomendado)
Se você estiver no Linux ou Mac, abra o terminal na pasta do projeto e rode:
```bash
chmod +x setup_pjt.sh
./setup_pjt.sh
```

### Opção B: Comandos Manuais (Passo a Passo)
Se preferir fazer manualmente ou estiver no Windows, rode estes comandos um por um:

1. **Compilar o app:**
   ```bash
   ./gradlew assembleFossDebug
   ```

2. **Instalar o APK:**
   ```bash
   adb install -r app/build/outputs/apk/foss/debug/Amarok-v0.10.1+64c1079-foss.apk
   ```

3. **Ativar Modo Deus:**
   ```bash
   adb shell dpm set-device-owner deltazero.amarok.foss/deltazero.amarok.receivers.AdminReceiver
   ```

## ✅ Como verificar se funcionou?
Rode o comando:
```bash
adb shell dpm list-owners
```
Se o retorno for `User 0: admin=deltazero.amarok.foss/...`, o tablet está protegido!

## 🛠️ Solução de Problemas
- **Erro "Already has a device owner":** Você precisa resetar o tablet ou remover o dono anterior.
- **Erro "Account exists":** Remova as contas do Google antes de rodar o comando.
- **Firewall não bloqueia:** Limpe o cache do Chrome ou reinicie o tablet após ativar o firewall no app.
