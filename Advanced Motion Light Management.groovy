import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.json.JsonOutput

@Field static boolean openByApp = true
@Field static boolean closedByApp = true
@Field static boolean setpointSentByApp = false

definition(
    name: "Thermostat Manager",
    namespace: "elfege",
    author: "ELFEGE",

    description: "Manage your thermostats with contact sensors, motion sensors and boost operations",

    category: "Green Living",
    iconUrl: "https://www.philonyc.com/assets/penrose.jpg",
    iconX2Url: "https://www.philonyc.com/assets/penrose.jpg",
    iconX3Url: "https://www.philonyc.com/assets/penrose.jpg", 
    image: "https://www.philonyc.com/assets/penrose.jpg"
)

preferences {

    page name: "settings"
}

def settings() {

    if(state.paused)
    {
        log.debug "new app label: ${app.label}"
        while(app.label.contains(" (Paused) "))
        {
            app.updateLabel(app.label.minus("(Paused)" ))
        }
        app.updateLabel(app.label + ("<font color = 'red'> (Paused) </font>" ))
    }
    else if(app.label.contains("(Paused)"))
    {
        app.updateLabel(app.label.minus("<font color = 'red'> (Paused) </font>" ))
        while(app.label.contains(" (Paused) ")){app.updateLabel(app.label.minus("(Paused)" ))}
        log.debug "new app label: ${app.label}"
    }

    if(state.paused == true)
    {
        state.button_name = "resume"
        log.debug "button name is: $state.button_name"
    }
    else 
    {
        state.button_name = "pause"
        log.debug "button name is: $state.button_name"
    }

    def pageProperties = [
        name:       "settings",
        title:      "Thermostats and other devices",
        nextPage:   null,
        install: true,
        uninstall: true
    ]

    dynamicPage(pageProperties) {

        section()
        {
            input "pause", "button", title: "$state.button_name"
            paragraph """

"""
        }

        section() {
            label title: "Assign a name", required: false
            input "restricted", "mode", title: "do not run this app while in these modes", multiple: true
        }

        section("Select the thermostat you want to control") { 

            input "thermostat", "capability.thermostat", title: "select a thermostat", required: true, multiple: false, description: null, submitOnChange:true
            input "boost", "bool", title: "boost this device", submitOnChange:true
            if(boost)
            {
                input "setCmd", "bool", title: "Add a custom command string", submitOnChange:true
                if(setCmd)
                {
                    input "boostMode", "text", title: "Write your command"
                }
                input "pw", "capability.powerMeter", title:"verify status with a power meter"

            }
            input "heatpump", "bool", title: "$thermostat is a heat pump or I want to add a secondary electric heater", submitOnChange:true
            if(heatpump)
            {
                input "heater", "capability.switch", title: "Select a switch to control an alternate heater", required: true, submitOnChange:true, multiple: false 
                if(heater)
                {
                    input "lowtemp", "number", title: "low temperature threshold", required: true, defaultValue: 30
                }
            }
            input "outsideTemp", "capability.temperatureMeasurement", title: "select a weather sensor for outside temperature", required:true, submitOnChange:true
            input "sensor", "capability.temperatureMeasurement", title: "select a temperature sensor (optional)", submitOnChange:true
            if(!sensor)
            {
                input "offrequiredbyuser", "bool", title: "turn off thermostat when desired temperature has been reached", defaultValue: false, submitOnChange:true
            }
            input "contact", "capability.contactSensor", title: "Turn off everything when these contacts are open", multiple: true, required: false, submitOnChange:true
            input "dimmer", "capability.switchLevel", title: "Use this dimmer as set point input source", required: true, submitOnChange:true


            input "outsideRef", "enum", title: "Select a temperature threshold reference method", required: true, options:["virtual dimmer", "static value"], submitOnChange:true
            if(outsideRef == "virtual dimmer")
            {                
                input "outsideThresDimmer", "capability.switchLevel", title: "Use this dimmer as a reference for outside temperature threshold", required: true, submitOnChange:true
            }
            else if(outsideRef == "static value")
            {
                input "outsideThreshold", "number", title: "type a numerical value representing the outside temperature threshold", required: true, submitOnChange:true, defaultValue:60 
            }
        }
        section("POWER SAVING")
        {
            input "motionSensors", "capability.motionSensor", title: "Save power when there's no motion", required: false, multiple: true, submitOnChange:true

            if(motion)
            {
                input "noMotionTime", "number", title: "after how long?", description: "Time in minutes"
                input "motionmodes", "mode", title: "Consider motion only in these modes", multiple: true, required: true 
            }  

            input "powersavingmode", "mode", title: "Save power when in one of these modes", required: false, multiple: true, submitOnChange: true
            if(powersavingmode)
            {
                input "criticalcold", "number", title: "Set a critical low temperature", required: true
                input "criticalhot", "number", title: "Set a critical high temperature", required: true
            }
        }

        section("Fans and other ways to coold down your home")
        {
            input "windows", "capability.switch", title: "Turn on those switches when home needs to cool down, wheather permitting", multiple:true, required: false, submitOnChange: true
            if(windows)
            {
                input "outsidetempwindowsH", "number", title: "Set a temperature below which it's ok to turn on $windows", required: true, submitOnChange: true
                input "outsidetempwindowsL", "number", title: "Set a temperature below which it's NOT ok to turn on $windows", required: true, submitOnChange: true
                if(outsidetempwindowsH && outsidetempwindowsL)
                {
                    paragraph "If outside temperature is between ${outsidetempwindowsL}F & ${outsidetempwindowsH}F, $windows will be used to coold down your place instead of your AC"

                    input "operationTime", "bool", title: "${windows}' operation must stop after a certain time", defaultValue:false, submitOnChange:true
                    if(operationTime)
                    {
                        input "windowsDuration", "number", title: "for how long?", description: "time in seconds", required: false
                        input "customCommand", "text", title: "custom command to stop operation (default is 'off()')", required: false, submitOnChange:true

                        if(customCommand)
                        {
                            def cmd = customCommand.contains("()") ? customCommand.minus("()") : customCommand
                            def windowsCmds = windows.findAll{it.hasCommand("${cmd}")}
                            boolean cmdOk = windowsCmds.size() == windows.size()
                            if(!cmdOk)
                            {
                                paragraph "<div style=\"width:102%;background-color:#1C2BB7;color:red;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">SORRY, THIS COMMAND $customCommand IS NOT SUPPORTED BY AT LEAST ONE OF YOUR DEVICES! Maybe a spelling error? In any case, make sure that each one of them support this command</div>"

                            }
                            else
                            {
                                paragraph """<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">The command $customCommand is supported by all your devices!</div> """

                            }
                        }
                    }
                }

            }
        }
        section()
        {
            input "run", "button", title: "RUN"
            input "update", "button", title: "UPDATE"
            input "poll", "button", title: "REFRESH"
            input "polldevices", "bool", title: "Poll devices"
            input "enabledebug", "bool", title: "Debug", submitOnChange:true
            input "description", "bool", title: "Description Text", submitOnChange:true

        }
    }
}
def installed() {
    logging("Installed with settings: ${settings}")

    initialize()
}
def updated() {

    logging("""updated with settings: ${settings}-""")
    unsubscribe()
    unschedule()
    initialize()
}
def initialize(){
    if(enabledebug)
    {
        log.warn "debug enabled"      
        state.EnableDebugTime = now()
        runIn(1800,disablelogging)
        descriptiontext "debug will be disabled in 30 minutes"
    }
    else 
    {
        log.warn "debug disabled"
    }
    state.paused = false
    state.restricted = false
    state.lastNeed = "heat"
    state.boostVal = 85
    state.boostMode = boostMode
    state.boostAttempt = now() as long
        state.offAttempt = now() as long

        state.lastMotionEvent = now() as long
        state.motionEvents = 0

    logging("subscribing to events...")

    subscribe(location, "mode", ChangedModeHandler) 
    subscribe(thermostat, "temperature", temperatureHandler)
    if(sensor)
    {
        subscribe(sensor, "temperature", temperatureHandler)
    }
    if(outsideThresDimmer)
    {
        subscribe(outsideThresDimmer, "level", outsideThresDimmerHandler)
    }
    subscribe(dimmer, "level", dimmerHandler)
    descriptiontext "subscribed $dimmer to dimmerHandler"
    subscribe(thermostat, "heatingSetpoint", setPointHandler)
    descriptiontext "subscribed ${thermostat}'s heatingSetpoint to setPointHandler"
    subscribe(thermostat, "coolingSetpoint", setPointHandler)
    descriptiontext "subscribed ${thermostat}'s coolingSetpoint to setPointHandler"

    if(motion)
    {
        subscribe(motion, "motion", motionHandler)
    }


    if(polldevices)
    {
        schedule("0 0/5 * * * ?", Poll)
    }

    schedule("0 0/1 * * * ?", mainloop)


    descriptiontext "INITIALIZATION DONE"

}
/************************************************HANDLERS*********************************************************/
def appButtonHandler(btn) {
    switch(btn) {
        case "pause":state.paused = !state.paused
        logging("state.paused = $state.paused")
        if(state.paused)
        {
            log.debug "unsuscribing from events..."
            unsubscribe()  
            log.debug "unschedule()..."
            unschedule()
            break
        }
        else
        {
            updated()            
            break
        }
        case "update":
        state.paused = false
        updated()
        break
        case "run":
        if(!state.paused) mainloop()
        break
        case "poll":
        Poll()
        break

    }
}
def ChangedModeHandler(evt)
{
    logging("mode is $evt.value")

    if(evt.value in restricted)
    {
        state.paused = true   
        state.restricted = true
    }
    else if(state.paused == true && state.restricted == true)
    {
        updated()
    }
}
def motionHandler(evt)
{

    logging("$evt.device is $evt.value")

    mainloop()

}
def temperatureHandler(evt)
{
    logging("$evt.device returns ${evt.value}F")
    mainloop()
}
def setPointHandler(evt)
{
    descriptiontext "new $evt.name is $evt.value---------------------------------------"

    def currDim = dimmer.currentValue("level")
    
    boolean notBoostVal = evt.value.toInteger() != state.boostVal.toInteger()
    boolean correspondingMode = (evt.name == "heatingSetpoint" && state.lastNeed == "heat") || (evt.name == "coolingSetpoint" && state.lastNeed == "cool")

    logging"""
setpointSentByApp = $setpointSentByApp
Current $dimmer value is $currDim
state.lastNeed = $state.lastNeed   
evt.value = $evt.value   
state.boostVal = ${state.boostVal.toInteger()}  
notBoostVal: ${notBoostVal}"""

    if(currDim != evt.value && notBoostVal && setpointSentByApp == false)
    {
        dimmer.setLevel(evt.value.toInteger()) // reverse definition goes into a loop when mid season, still need to figure that one out
        descriptiontext "$dimmer set to $evt.value"
    }

    else if(!notBoostVal)
    {
        log.warn "BOOST VALUE, not adjusting dimmer"   
    }
    else 
    {
        logging("dimmer level ok (${dimmer.currentValue("level")} == ${evt.value}")
    }
    if(setpointSentByApp == true) 
    {
        setpointSentByApp = false
    }
    mainloop()
}
def dimmerHandler(evt)
{
    descriptiontext "new dimmer level is $evt.value"
    mainloop()
}
def outsideThresDimmerHandler()
{
    descriptiontext "Ouside threshold value is now: $evt.value"
    mainloop()

}
/************************************************MAIN loop*********************************************************/
def mainloop()
{
    if(!state.paused)
    {
        if(state.EnableDebugTime == null) state.EnableDebugTime = now()
        if(enabledebug && now() - state.EnableDebugTime > 1800000)
        {
            descriptiontext "Debug has been up for too long..."
            disablelogging() 
        }

        def therMode = thermostat.currentValue("thermostatMode")
        logging("$thermostat is in $therMode mode 54dfg")

        if(therMode == "off")
        {
            state.boostOk = false
        }
        if(pw)
        {
            logging("$pw power meter returns ${pw.currentValue("power")}Watts")
        }
        if(therMode != "auto")
        {

            int desired = getDesired()
            def needData = getNeed(desired)
            def need = needData[1]
            logging("need is needData[1] = $need")
            state.boostVal = 85

            if(boost && need in ["heat", "cool"])
            {
                logging("BOOST MODE!")

                if(need == "heat")
                {
                    logging("setting state.boostVal to heating boost value")
                    state.boostVal = 85

                }
                else if(need == "cool")
                {
                    logging("setting state.boostVal to cooling boost value")
                    state.boostVal = 66
                }

                logging("state.boostVal = $state.boostVal")
                desired = state.boostVal

                if(boostMode.contains("()"))
                {
                    // do nothing
                }
                else
                {
                    boostMode + "()"
                }

                logging "boostMode command is: $boostMode"

                desired = state.boostVal
            }


            def cmd = "set"+"${needData[0]}"+"ingSetpoint"

            def currSP = thermostat.currentValue("thermostatSetpoint").toInteger()
            logging("therMode = $therMode currSP = $currSP")

            virtualThermostat(need)


            if(therMode != need)
            {
                if(need != "off" || (need == "off" && (sensor || offrequiredbyuser)))
                {
                    thermostat.setThermostatMode(need) // set desired mode
                    logging("THERMOSTAT SET TO $need mode (587gf)")
                    if(need == "off")
                    {
                        state.offAttempt = now() as long
                            }
                }
                else 
                {
                    logging("THERMOSTAT stays in $therMode mode")
                }

            }
            else if(need != "off")
            {
                logging("Thermostat already set to $need mode")
            }

            logging("currSP != desired -> ${currSP != desired} -> ${currSP} != ${desired} ")
            if(need != "off" && currSP.toInteger() != desired)
            {
                if(boost)
                {
                    pauseExecution(2000)
                }
                setpointSentByApp = true
                thermostat."${cmd}"(desired)   // set desired temp
                logging("THERMOSTAT SET TO $desired (564fdevrt)")
            }
            else if(need != "off")
            {
                logging("Thermostat already set to $desired")
            }

            if(boost && need != "off" && !state.boostOk)
            {
                state.boostOk = true
                state.boostAttempt = now() as long
                    setpointSentByApp = true
                pauseExecution(5000)
                thermostat."${boostMode}"
                logging("THERMOSTAT SET TO $boostMode mode")
            }
            else 
            {
                if(need == "off")
                {
                    state.boostOk = false
                }
                if(state.boostOk)
                {
                    logging("Thermostat already set to $state.boostMode mode")
                }
            }
            if(pw)
            {
                // here we manage possible failure for a thermostat to have received the z-wave/zigbee or http command
                long timeElapsedSinceLastBoost = now() - state.boostAttempt
                long timeElapsedSinceLastOff = now() - state.offAttempt // when device driver returns state off while in fact signal didn't go through
                long threshold = 4 * 60 * 1000 // give it 4 minutes to kick in before attempting new request 
                boolean timeIsUp = timeElapsedSinceLastBoost > threshold
                boolean timeIsUpOff = timeElapsedSinceLastOff > 30000
                boolean pwLow = pw.currentValue("power") < 600
                logging("time since last boost Attempt = ${timeElapsedSinceLastBoost/1000} seconds & threshold = ${threshold/1000}sec")
                logging("time since last OFF Attempt = ${timeElapsedSinceLastOff/1000} seconds & threshold = ${30}sec")

                if(boost && timeIsUp && pwLow && need != "off")
                {
                    log.warn "SELF HEALING ATTEMPT"
                    state.boostAttempt = now() as long
                        logging("re-sending ${cmd}($desired)...")
                    setpointSentByApp = true
                    thermostat."${cmd}"(desired) // set the desired boost temperature value
                    logging("waiting 10 seconds")
                    pauseExecution(10000) // give plenty of times for respective commands to be received and processed by the device driver
                    logging("sending $boostMode command") 
                    thermostat."${boostMode}"// send the boost command as defined by the user
                    pauseExecution(10000) // give plenty of times for respective commands to be received and processed by the device driver
                    Poll()
                }
                else if(timeIsUpOff && need == "off" && !pwLow)
                {
                    log.warn("$thermostat should be off but still draining power, resending cmd")
                    state.offAttempt = now() as long
                        thermostat.setThermostatMode("off")
                    thermostat.off()
                    Poll()
                }
                else if(timeIsUpOff && need == "off" && !pwLow)
                {
                    logging("$thermostat is off (pw), skipping off repair")   
                }
                else if((!pwLow &&  need in ["heat", "cool"]) || (need == "off" && pwLow))
                {
                    logging("EVERYTHING OK")
                }
                else 
                {
                    logging("Auto Fix Should Kick in within time threshold")
                }
            }
        }
        else 
        {
            descriptiontext("OVERRIDE MODE--------------")   
        }
    }
}

def virtualThermostat(need)
{
    if(heatpump)
    {
        def outsideTemp = outsideTemp.currentValue("temperature") // only needed if electric heater here
        logging("outsideTemp < lowtemp ? ${outsideTemp < lowtemp}")
        if(need == "heat" && outsideTemp < lowtemp.toInteger())
        {
            boolean powercap = heater.hasAttribute("power")
            logging("is heater power meter capable? $powercap")
            boolean powerok = powercap ? (heater.currentValue("power") > 100) : true
            logging "$heater power consumption is ${powerok ? "ok" : "not as expected"} ${powercap ? "${heater.currentValue("power")}watts" : ''}"
            if(heater.currentValue("switch") != "on" || !powerok)
            {
                logging("Turning $heater on because outside temperature is currently ${outsideTemp}°F")
                heater.on()   
            }
            else 
            {
                logging("$heater is on because outside temperature is currently ${outsideTemp}°F")
            }
        }
        else 
        {


            if(heater.currentValue("switch") != "off")
            {
                logging("Turning $heater off")
                heater.off()
            }
            else 
            {

                logging("$heater already off")
            }
        }
    }
}
/************************************************INPUTS*********************************************************/
def getDesired()
{
    int desired = 70
    if(!Active())
    {
        desired = dimmer.currentValue("level") - 6
    }
    else {
        desired = dimmer.currentValue("level")
    }

    logging("desired temperature is: $desired and current temperature is ${getInsideTemp()}")
    return desired
}
def getNeed(desired)
{

    boolean INpwSavingMode = powersavingmode && location.mode in powersavingmode
    logging """
INpwSavingMode = $INpwSavingMode
location.mode in powersavingmode = ${location.mode in powersavingmode}
powersavingmode = $powersavingmode
"""

    int outsideTemp = outsideTemp.currentValue("temperature")
    def need0 = ""
    def need1 = ""
    def need = []
    def inside = getInsideTemp()

    boolean contactClosed = !contactsAreOpen()  
    logging("contactClosed = $contactClosed")
    def outsideThres = getOutsideThershold()
    logging("outside Temperature Threshold is currently: $outsideThres inside = $inside")

    if(!INpwSavingMode)
    {
        if(outsideTemp > outsideThres && inside >= desired /*+ 0.5*/ && contactClosed && Active)
        {
            need0 = "Cool"// capital letter for later construction of the setCoolingSetpoint cmd
            need1 = "cool"
            state.lastNeed = need1
            logging("need and state.lastNeed set to ${[need0,need1]}")
        }
        else if(outsideTemp < outsideThres && inside <= desired /*- 0.5*/ && contactClosed && Active)
        {
            need0 = "Heat" // capital letter for later construction of the setHeatingSetpoint cmd
            need1 = "heat"
            state.lastNeed = need1
            logging("need and state.lastNeed set  to ${[need0,need1]}")
        }
        else
        {
            need0 = "off"
            need1 = "off"
            logging("need set to OFF")
        }

    }
    else   // POWER SAVING MODE      
    { 
        logging """
inside < criticalhot :  ${inside < criticalhot}
inside > criticalcold :  ${inside > criticalcold}
"""
        if(inside < criticalhot)
        {
            descriptiontext "POWER SAVING MODE"
            need0 = "off"
            need1 = "off"
        }
        else if(inside > criticalcold)
        {
            descriptiontext "POWER SAVING MODE"
            need0 = "off"
            need1 = "off"
        }
    }

    //INpwSavingMode = true
    if(windows)
    {
        boolean outsideWithinRange = outsideTemp < outsidetempwindowsH && outsideTemp > outsidetempwindowsL
        boolean needFreshAir = inside >= desired + 2
        if(INpwSavingMode)
        {
            outsideWithinRange = outsideTemp < criticalhot && outsideTemp > criticalcold
            needFreshAir = inside < criticalhot && inside > criticalcold
        }
        boolean someAreOff = windows.findAll{it.currentValue("switch") == "off"}
        logging """
openByApp = $openByApp
closedByApp = $closedByApp
outsideTemp = $outsideTemp
inside temperature = $inside
desired = $desired
outsideWithinRange = $outsideWithinRange
needFreshAir = $needFreshAir
someAreOff = $someAreOff
"""
        //closedByApp = true

        if(needFreshAir && outsideWithinRange)
        {
            descriptiontext "$windows INSTEAD OF AC"
            need0 = "off"
            need1 = "off"


            if(someAreOff)
            {
                if(closedByApp)
                {
                    log.warn "opening $windows"
                    windows.on()
                    openByApp = true
                    closedByApp = false
                    if(operationTime)
                    {
                        runIn(windowsDuration, stop)
                    }

                }
                else
                {
                    descriptiontext "$windows were not open by this app"
                }

            }
            else
            {
                descriptiontext "$windows already open"
            }
        }
        else if(!someAreOff)
        {
            if(openByApp)
            {
                log.warn "closing $windows"
                unschedule(stop)
                windows.off()
                openByApp = false
                closedByApp = true
            }
            else
            {
                descriptiontext "$windows were not open by this app"
            }

        }
        else
        {
            descriptiontext "$windows already closed"
        }

    }

    need = [need0, need1]
    descriptiontext ("need = $need")
    return need

}
def getInsideTemp()
{
    def inside = thermostat.currentValue("temperature")
    if(sensor)
    {
        inside = sensor.currentValue("temperature")
    }

    return inside
}
private getOutsideThershold()
{
    if(outsideThresDimmer)
    {
        return outsideThresDimmer.currentValue("level")
    }
    else if(outsideThreshold)
    {
        return outsideThreshold
    }
    else 
    {
        return 60
    }
}
def Poll()
{


    if(polldevices)
    {
        boolean thermPoll = thermostat.hasCommand("poll")
        boolean thermRefresh = thermostat.hasCommand("refresh") 


        boolean outsidePoll = outsideTemp.hasCommand("poll")
        boolean outsideRefresh = outsideTemp.hasCommand("refresh")
        boolean override = state.override

        if(thermRefresh){
            thermostat.refresh()
            logging("refreshing $thermostat")
        }
        else if(thermPoll){
            thermostat.poll()
            logging("polling $thermostat")
        }
        if(outsideRefresh){
            outsideTemp.refresh()
            logging("refreshing $outsideTemp")
        }
        else if(outsidePoll){
            outsideTemp.poll()
            logging("polling $outsideTemp")
        }

        boolean heaterPoll = heater?.hasCommand("poll")
        boolean heaterRefresh = heater?.hasCommand("refresh") 

        if(heaterRefresh){
            heater.refresh()
            logging("refreshing $heater")
        }
        else if(heaterPoll){
            heater.poll()
            logging("polling $heater")
        }


        boolean sensorPoll = sensor?.hasCommand("poll")
        boolean sensorRefresh = sensor?.hasCommand("refresh") 

        if(sensorRefresh){
            sensor.refresh()
            logging("refreshing $sensor")
        }
        else if(sensorPoll){
            sensor.poll()
            logging("polling $sensor")
        }

    }

}
/************************************************BOOLEANS*********************************************************/
boolean contactsAreOpen() 
{
    boolean Open = false
    if(contact)
    {
        def s = contact.size()
        def i = 0

        for(s!=0;i<s;i++)
        {
            if(contact[i]?.currentValue("contact") == "open")
            {
                Open = true
                break
            }
        }
    }
    logging("$contact open ?: $Open")
    return Open
}
boolean Active()
{
    boolean result = true // default is true  always return Active = true when no sensor is selected by the user


    if(motionSensors)
    {
        long Dtime = noMotionTime * 1000 * 60
        int s = motionSensors.size() 
        int i = 0
        def thisDeviceEvents = []
        int events = 0

        if(location.mode in motionmodes)
        {
            for(s != 0; i < s; i++) // collect active events
            { 
                thisDeviceEvents = motionSensors[i].eventsSince(new Date(now() - Dtime)).findAll{it.value == "active"} // collect motion events for each sensor separately
                events += thisDeviceEvents.size() 
            }
            descriptiontext "$events active events in the last ${noMotionTime} minutes"
            result = events > 0 

        }
        else 
        {
            logging("motion returns true because outside of motion modes")
        }
        logging("user did not select any motion sensor")
    }
    return result
}


/************************************************OTHER*********************************************************/
def stop()
{

    if(customCommand)
    {
        log.warn "$windows $customCommand"
        windows.stop() //"${customCommand}"
    }
    else
    {
        log.warn "$windows off"
        windows.off()
    }
}

def logging(message)
{
    if(enabledebug)
    {
        log.debug message
    }
}
def descriptiontext(message)
{
    if(description)
    {
        log.info message
    }
}
def disablelogging(){
    log.warn "debug logging disabled..."
    app.updateSetting("enabledebug",[value:"false",type:"bool"])
}
