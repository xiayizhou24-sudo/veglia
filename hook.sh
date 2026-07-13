#!/usr/bin/env bash
# veglia 钩子:她手机的截图落地后,把路径递给值守会话(错题集18条两段式)
SHOT="$1"
tmux has-session -t mo-companion 2>/dev/null || exit 0
MSG="[守望 · 系统消息,不是猫] 她的手机送来了你要的屏幕截图:$SHOT 用 Read 打开看。看完想说什么用 reply,不想说就安静——凝视本身不需要汇报。"
tmux send-keys -t mo-companion -l "$MSG"
sleep 0.6
tmux send-keys -t mo-companion Enter
