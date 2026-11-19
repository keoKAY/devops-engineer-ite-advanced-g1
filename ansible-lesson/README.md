## NOTE for the ansible lessons 

```bash
sudo apt update
sudo apt install software-properties-common
sudo add-apt-repository --yes --update ppa:ansible/ansible
sudo apt install ansible

ansible --version 
```
Our Folder Structure 
```bash
.
├── credentials
│   ├── ansible_key
│   └── vault1-pass.txt
├── lab1
│   ├── ansible.cfg
│   ├── inventory.ini
│   ├── Justfile
│   ├── playbooks
│   │   └── basic.yaml
│   └── vars
│       └── demo-vars.yaml
└── README.md
```

```bash
ssh-keygen -t ed25519 -f ~/.ssh/ansible_key -C "Ansible automation key"
```

```bash
ansible-vault --help 

ansible-vault encrypt credentials/demo-vars.yaml 
ansible-vault view credentials/demo-vars.yaml 

ansible-playbook --ask-vault-pass # type password of vault 
ansible-playbook --vault-password-file filename # no need type the password 
```