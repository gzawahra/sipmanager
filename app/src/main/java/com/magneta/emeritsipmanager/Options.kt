package com.magneta.emeritsipmanager

import android.os.Build

class Options {


    companion object{

        const val DEVICE_E_WG200 : String = "E-WG200"
        const val DEVICE_E_WWB100 : String = "E-WWB100"

        fun getDeviceModel() : String{
            return Build.MODEL
        }
    }
}