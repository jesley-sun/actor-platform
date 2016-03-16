/*
 * Copyright (C) 2015 Actor LLC. <https://actor.im>
 */

package im.actor.core.modules.messaging.dialogs;

import java.util.ArrayList;
import java.util.List;

import im.actor.core.entity.Avatar;
import im.actor.core.entity.ContentDescription;
import im.actor.core.entity.ContentType;
import im.actor.core.entity.Dialog;
import im.actor.core.entity.DialogBuilder;
import im.actor.core.entity.DialogState;
import im.actor.core.entity.Group;
import im.actor.core.entity.Message;
import im.actor.core.entity.MessageState;
import im.actor.core.entity.Peer;
import im.actor.core.entity.User;
import im.actor.core.entity.content.AbsContent;
import im.actor.core.modules.ModuleContext;
import im.actor.core.modules.messaging.dialogs.messages.DialogClear;
import im.actor.core.modules.messaging.dialogs.messages.DialogDelete;
import im.actor.core.modules.messaging.dialogs.messages.DialogCounterChanged;
import im.actor.core.modules.messaging.dialogs.messages.DialogsLoaded;
import im.actor.core.modules.messaging.dialogs.messages.DialogsRead;
import im.actor.core.modules.messaging.dialogs.messages.DialogsReceive;
import im.actor.core.modules.messaging.dialogs.messages.GroupChanged;
import im.actor.core.modules.messaging.dialogs.messages.InMessage;
import im.actor.core.modules.messaging.dialogs.messages.MessageContentChanged;
import im.actor.core.modules.messaging.dialogs.messages.MessageDeleted;
import im.actor.core.modules.messaging.dialogs.messages.MessageStateChanged;
import im.actor.core.modules.messaging.dialogs.messages.UserChanged;
import im.actor.core.modules.messaging.entity.DialogHistory;
import im.actor.core.util.ModuleActor;
import im.actor.runtime.Log;
import im.actor.runtime.Runtime;
import im.actor.runtime.annotations.Verified;
import im.actor.runtime.promise.Promise;
import im.actor.runtime.storage.IoResult;
import im.actor.runtime.storage.ListEngine;

import static im.actor.core.util.JavaUtil.equalsE;

public class DialogsActor extends ModuleActor {

    private final ListEngine<Dialog> dialogs;
    private Boolean isEmpty;
    private Boolean emptyNotified;

    public DialogsActor(ListEngine<Dialog> dialogs, ModuleContext context) {
        super(context);
        this.dialogs = dialogs;
    }

    @Override
    public void preStart() {
        super.preStart();
        notifyState(true);
    }

    @Verified
    private Promise<IoResult> onMessage(Peer peer, Message message, boolean forceWrite, int counter) {
        PeerDesc peerDesc = buildPeerDesc(peer);
        if (peerDesc == null) {
            return IoResult.OK();
        }

        if (message == null) {
            // Ignore empty message if not forcing write
            if (!forceWrite) {
                return IoResult.OK();
            }

            // Else perform chat clear
            onChatClear(peer);
        } else {

            ContentDescription contentDescription = ContentDescription.fromContent(message.getContent());
            Dialog dialog = dialogs.getValue(peer.getUnuqueId());

            //
            // Filtering Incorrect
            //
            if (dialog != null) {
                if (!forceWrite && dialog.getSortDate() > message.getSortDate()) {
                    return IoResult.OK();
                }
            } else {
                // Do not create dialogs for silent messages
                if (contentDescription.isSilent()) {
                    return IoResult.OK();
                }
            }


            DialogBuilder builder = new DialogBuilder()
                    .setRid(message.getRid())
                    .setTime(message.getDate())
                    .setMessageType(contentDescription.getContentType())
                    .setText(contentDescription.getText())
                    .setRelatedUid(contentDescription.getRelatedUser())
                    .setSenderId(message.getSenderId());

            if (counter >= 0) {
                builder.setUnreadCount(counter);
            }

            boolean forceUpdate = false;

            if (dialog != null) {

                builder.setPeer(dialog.getPeer())
                        .setDialogTitle(dialog.getDialogTitle())
                        .setDialogAvatar(dialog.getDialogAvatar())
                        .setSortKey(dialog.getSortDate());

                // Do not push up dialogs for silent messages
                if (!contentDescription.isSilent()) {
                    builder.setSortKey(message.getSortDate());
                }

            } else {
                builder.setPeer(peer)
                        .setDialogTitle(peerDesc.getTitle())
                        .setDialogAvatar(peerDesc.getAvatar())
                        .setSortKey(message.getSortDate());

                forceUpdate = true;
            }

            addOrUpdateItem(builder.createDialog());
            notifyState(forceUpdate);
        }

        return IoResult.OK();
    }

    @Verified
    private void onUserChanged(User user) {
        Dialog dialog = dialogs.getValue(user.peer().getUnuqueId());
        if (dialog != null) {
            // Ignore if nothing changed
            if (dialog.getDialogTitle().equals(user.getName())
                    && equalsE(dialog.getDialogAvatar(), user.getAvatar())) {
                return;
            }

            // Update dialog peer info
            Dialog updated = dialog.editPeerInfo(user.getName(), user.getAvatar());
            addOrUpdateItem(updated);
            updateSearch(updated);
        }
    }

    @Verified
    private void onGroupChanged(Group group) {
        Dialog dialog = dialogs.getValue(group.peer().getUnuqueId());
        if (dialog != null) {
            // Ignore if nothing changed
            if (dialog.getDialogTitle().equals(group.getTitle())
                    && equalsE(dialog.getDialogAvatar(), group.getAvatar())) {
                return;
            }

            // Update dialog peer info
            Dialog updated = dialog.editPeerInfo(group.getTitle(), group.getAvatar());
            addOrUpdateItem(updated);
            updateSearch(updated);
        }
    }

    @Verified
    private void onChatDeleted(Peer peer) {
        dialogs.removeItem(peer.getUnuqueId());
        notifyState(true);
    }

    @Verified
    private void onChatClear(Peer peer) {
        Dialog dialog = dialogs.getValue(peer.getUnuqueId());

        // If we have dialog for this peer
        if (dialog != null) {

            // Update dialog
            addOrUpdateItem(new DialogBuilder(dialog)
                    .setMessageType(ContentType.NONE)
                    .setText("")
                    .setTime(0)
                    .setUnreadCount(0)
                    .setRid(0)
                    .setSenderId(0)
                    .createDialog());
        }
    }

    @Verified
    private void onChatRead(Peer peer, long readDate) {
        Dialog dialog = dialogs.getValue(peer.getUnuqueId());
        if (dialog != null) {
            DialogBuilder builder = new DialogBuilder(dialog);
            if (readDate > dialog.getReadDate()) {
                builder.setReadDate(readDate);
            }
            if (readDate > dialog.getReceivedDate()) {
                builder.setReceiveDate(readDate);
            }
            dialogs.addOrUpdateItem(builder.createDialog());
        }
    }

    @Verified
    private void onChatReceive(Peer peer, long receiveDate) {
        Dialog dialog = dialogs.getValue(peer.getUnuqueId());
        if (dialog != null) {
            DialogBuilder builder = new DialogBuilder(dialog);
            if (receiveDate > dialog.getReceivedDate()) {
                builder.setReceiveDate(receiveDate);
            }
            dialogs.addOrUpdateItem(builder.createDialog());
        }
    }

    @Verified
    private void onMessageContentChanged(Peer peer, long rid, AbsContent content) {
        Dialog dialog = dialogs.getValue(peer.getUnuqueId());

        // If message is on top
        if (dialog != null && dialog.getRid() == rid) {

            // Update dialog
            ContentDescription description = ContentDescription.fromContent(content);
            addOrUpdateItem(new DialogBuilder(dialog)
                    .setText(description.getText())
                    .setRelatedUid(description.getRelatedUser())
                    .setMessageType(description.getContentType())
                    .createDialog());
        }
    }

    @Verified
    private void onCounterChanged(Peer peer, int count) {
        Dialog dialog = dialogs.getValue(peer.getUnuqueId());

        // If we have dialog for this peer
        if (dialog != null) {

            // Counter not actually changed
            if (dialog.getUnreadCount() == count) {
                return;
            }

            // Update dialog
            addOrUpdateItem(new DialogBuilder(dialog)
                    .setUnreadCount(count)
                    .createDialog());
        }
    }

    @Verified
    private void onHistoryLoaded(List<DialogHistory> history) {
        ArrayList<Dialog> updated = new ArrayList<Dialog>();
        for (DialogHistory dialogHistory : history) {
            // Ignore already available dialogs
            if (dialogs.getValue(dialogHistory.getPeer().getUnuqueId()) != null) {
                continue;
            }

            PeerDesc peerDesc = buildPeerDesc(dialogHistory.getPeer());
            if (peerDesc == null) {
                continue;
            }

            ContentDescription description = ContentDescription.fromContent(dialogHistory.getContent());

//            updated.add(new Dialog(dialogHistory.getPeer(),
//                    dialogHistory.getSortDate(), peerDesc.getTitle(), peerDesc.getAvatar(),
//                    dialogHistory.getUnreadCount(),
//                    dialogHistory.getRid(), description.getContentType(), description.getText(), dialogHistory.getStatus(),
//                    dialogHistory.getSenderId(), dialogHistory.getDate(), description.getRelatedUser()));
        }
        addOrUpdateItems(updated);
        updateSearch(updated);
        context().getAppStateModule().onDialogsLoaded();
        notifyState(true);
    }

    // Utils

    private void addOrUpdateItems(List<Dialog> updated) {
        dialogs.addOrUpdateItems(updated);
    }

    private void addOrUpdateItem(Dialog dialog) {
        dialogs.addOrUpdateItem(dialog);
    }

    private void updateSearch(Dialog dialog) {
        ArrayList<Dialog> d = new ArrayList<>();
        d.add(dialog);
        context().getSearchModule().onDialogsChanged(d);
    }

    private void updateSearch(List<Dialog> updated) {
        context().getSearchModule().onDialogsChanged(updated);
    }

    private void notifyState(boolean force) {
        if (isEmpty == null || force) {
            isEmpty = this.dialogs.isEmpty();
        }

        if (!isEmpty.equals(emptyNotified)) {
            emptyNotified = isEmpty;
            context().getAppStateModule().onDialogsUpdate(isEmpty);
        }
    }

    @Verified
    private PeerDesc buildPeerDesc(Peer peer) {
        switch (peer.getPeerType()) {
            case PRIVATE:
                User u = getUser(peer.getPeerId());
                return new PeerDesc(u.getName(), u.getAvatar());
            case GROUP:
                Group g = getGroup(peer.getPeerId());
                return new PeerDesc(g.getTitle(), g.getAvatar());
            default:
                return null;
        }
    }

    private class PeerDesc {

        private String title;
        private Avatar avatar;

        private PeerDesc(String title, Avatar avatar) {
            this.title = title;
            this.avatar = avatar;
        }

        public String getTitle() {
            return title;
        }

        public Avatar getAvatar() {
            return avatar;
        }
    }

    // Messages

    @Override
    public void onReceive(Object message) {
        if (message instanceof UserChanged) {
            UserChanged userChanged = (UserChanged) message;
            onUserChanged(userChanged.getUser());
        } else if (message instanceof DialogClear) {
            onChatClear(((DialogClear) message).getPeer());
        } else if (message instanceof DialogDelete) {
            onChatDeleted(((DialogDelete) message).getPeer());
        } else if (message instanceof MessageDeleted) {
            MessageDeleted deleted = (MessageDeleted) message;
            onMessage(deleted.getPeer(), deleted.getTopMessage(), true, -1);
        } else if (message instanceof DialogsLoaded) {
            DialogsLoaded historyLoaded = (DialogsLoaded) message;
            onHistoryLoaded(historyLoaded.getHistory());
        } else if (message instanceof GroupChanged) {
            GroupChanged groupChanged = (GroupChanged) message;
            onGroupChanged(groupChanged.getGroup());
        } else if (message instanceof MessageContentChanged) {
            MessageContentChanged contentChanged = (MessageContentChanged) message;
            onMessageContentChanged(contentChanged.getPeer(), contentChanged.getRid(),
                    contentChanged.getContent());
        } else if (message instanceof DialogCounterChanged) {
            DialogCounterChanged dialogCounterChanged = (DialogCounterChanged) message;
            onCounterChanged(dialogCounterChanged.getPeer(), dialogCounterChanged.getCounter());
        } else if (message instanceof DialogsReceive) {
            DialogsReceive dialogsReceive = (DialogsReceive) message;
            onChatReceive(dialogsReceive.getPeer(), dialogsReceive.getReceiveDate());
        } else if (message instanceof DialogsRead) {
            DialogsRead dialogsRead = (DialogsRead) message;
            onChatRead(dialogsRead.getPeer(), dialogsRead.getReadDate());
        } else {
            super.onReceive(message);
        }
    }

    @Override
    public Promise onAsk(Object message) throws Exception {
        if (message instanceof InMessage) {
            InMessage inMessage = (InMessage) message;
            return onMessage(inMessage.getPeer(), inMessage.getMessage(), false, inMessage.getCounter());
        } else {
            return super.onAsk(message);
        }
    }
}