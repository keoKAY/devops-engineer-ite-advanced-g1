## NOTE FOR ITE DEVOPS 

```bash
sudo apt update
sudo apt install fontconfig openjdk-21-jre
java -version



sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2026.key
echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc]" \
  https://pkg.jenkins.io/debian-stable binary/ | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt update
sudo apt install jenkins


sudo systemctl status jenkins 
curl localhost:8080


# clean up jenkins 
sudo systemctl stop jenkins
sudo apt-get remove --purge jenkins

# Remove build history, configuration files, and plugin data
sudo rm -rf /var/lib/jenkins

# Remove system logs
sudo rm -rf /var/log/jenkins

# Remove operational cache files
sudo rm -rf /var/cache/jenkins

```