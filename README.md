## NOte for the devops engineering ite 

```bash
# ubuntu/jammy64 
vagrant init ubuntu/jammy64 


# for mac M chip 
vagrant init bento/ubuntu-24.04 
vagrant up # turn on the vm 

vagrant status  
vagrant ssh # remote to vm 
# inside the vm, we update the packages 
sudo apt update && sudo apt upgrade -y 


# to install nginx 
sudo apt install nginx -y 
sudo apt install neofetch -y 

sudo apt install nginx neofetch -y 
curl localhost:80 # send http to test the service reachable
curl localhost:3000 

which nginx 
```
*** 
## Setting up OMZ and TLDR 
> Note: help you work better with terminal 
```bash 
which zsh 
sudo apt install zsh -y 

# omz 
sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)"
```

- Install plugins to make zsh smarter 
```bash
git clone https://github.com/zsh-users/zsh-autosuggestions.git $ZSH_CUSTOM/plugins/zsh-autosuggestions

git clone https://github.com/zsh-users/zsh-syntax-highlighting.git $ZSH_CUSTOM/plugins/zsh-syntax-highlighting



# edit .zshrc to use the plugins 
nano ~/.zshrc 
# find the config that start with plugins=(git)

plugins=(git zsh-autosuggestions zsh-syntax-highlighting )

# to save the configuration inside nano editor 
# control + O + enter 
# control + x + enter

# to apply the configuration 
source ~/.zshrc 
```

- Install tldr for learning about linux commands 
```bash 
useradd 
man useradd # open the manual for useradd command

# this one is not working 
sudo apt install tldr -y 
npm install -g tldr 

sudo snap install tldr 
sudo apt remove tldr 
sudo apt autoremove 


```