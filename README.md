## Introduction

This Mod saves chat logs to the disk to prevent them from being lost when exiting.

Compared with the plain log file (i.e. logs in the "logs" folder), the saved logs here support additional styles including colors, fonts, and even hovering texts. Besides, the time information when messages are received, which is absent in the vanilla game, is available here.

## Filtering sessions

Also, searching for sessions or messages in the history is supported, you may filter sessions by: 

- The save or server associated with the session.
- The day, month, or year in which the session started.
- The number of messages in the session.
- The length of the session.

## Settings

- `visibleLineCount`: Overrides the maximum number of messages in the chatting HUD.
- `autoSaveIntervalInMs`: The interval of the auto-saving of chat logs, in milliseconds.

## Tooltips

If a text in the chat logs has a click event or hover event, a hovering tooltip is displayed, with the argument of the event on it. When both click event and hover event are present in the same text, only the argument of the hover event is displayed initially, to get the argument of the click event, press the Alt key.

The texts on the tooltips can be copied by clicking the text with Ctrl down.