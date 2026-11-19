## NOTE FOR ANSIBLE IAC 
> Ansible is required to run this project 
```bash
sudo apt update
sudo apt install software-properties-common
sudo add-apt-repository --yes --update ppa:ansible/ansible
sudo apt install ansible

```
- Additional tools 
```bash 
sudo apt install fish tree -y 
sudo snap install just --classic 
```

### How get the ADC File for our playbooks 
```bash

# for gcp instance, thsi is already installed 
sudo snap install google-cloud-cli --classic

# for gcp instance, you only need this
# don't need to set the adc_file config 
gcloud auth application-default login


```