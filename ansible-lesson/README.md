## NOTE for the ansible lessons 

```bash
sudo apt update
sudo apt install software-properties-common
sudo add-apt-repository --yes --update ppa:ansible/ansible
sudo apt install ansible

ansible --version 
```


```bash
ssh-keygen -t ed25519 -f ~/.ssh/ansible_key -C "Ansible automation key"
