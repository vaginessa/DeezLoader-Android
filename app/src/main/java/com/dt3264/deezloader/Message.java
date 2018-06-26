package com.dt3264.deezloader;

import io.reactivex.Observable;

public class Message {
    private final String message;
    private final int type;
    //Type=1 -> Site ready
    //Type=2 -> New folder request
    //Type=3 -> New folder result
    //Type=4 -> Exit app

    public Message(int _type, String _message) {
        this.message = _message;
        this.type = _type;
    }

    public String getMessage() {
        return message;
    }

    public int getType() {
        return type;
    }

    public static Observable<Message> transmitMessage(Message msg) {
        return Observable.just(msg);
    }
}
