package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private SecurityService securityService;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private Sensor sensor;

    @Mock
    private StatusListener statusListener;

    private Set<Sensor> createSensors(int numberOfSensors, boolean active) {
        Set<Sensor> sensors = new HashSet<>();

        for (int i = 0; i < numberOfSensors; i++) {
            sensors.add(new Sensor("sensor#"+i, SensorType.DOOR));
        }
        sensors.forEach(s -> s.setActive(active));
        return sensors;
    }

    @BeforeEach
    void init(){
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor("test", SensorType.DOOR);
    }

    @Test  // 1 If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    public void changeSensorActivationStatus_armed_activated_setPendingAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @Test // 2 If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
    public void changeSensorActivationStatus_armed_activated_pending_setAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test //3.If pending alarm and all sensors are inactive, return to no alarm state.
    public void changeSensorActivationStatus_pending_inactive_setNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.changeSensorActivationStatus(sensor,true);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest // 4 If alarm is active, change in sensor state should not affect the alarm state.
    @ValueSource(booleans = {true})
    public void changeSensorActivationStatus_alarmActive_changeSensor_returnAlarmNotAffected(boolean sensorStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, sensorStatus);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test // 5 If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    public void changeSensorActivationStatus_sensorActivated_activeSensor_pendingAlarm_returnAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test // 6 If a sensor is deactivated while already inactive, make no changes to the alarm state.
    public void changeSensorActivationStatus_sensorInactive_deactivateSensor_returnAlarmNoChanges() {
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    // Test 7
    @Test // 7 If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    public void processImage_hasCat_armed_returnAlarmOn() {
        BufferedImage image = new BufferedImage(1,1,1);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(image);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // Test 8 If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @Test
    public void processImage_noCat_sensorInactive_returnNoAlarm() {
        Set<Sensor> sensors = createSensors(3, false);
        BufferedImage image = new BufferedImage(1,1,1);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        securityService.processImage(image);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // Test 9 If the system is disarmed, set the status to no alarm.
    @Test
    public void setArmingStatus_disarmed_returnNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // Test 10 If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    public void setArmingStatus_armed_returnSensorsInactive(ArmingStatus armingStatus) {
        Set<Sensor> sensors = createSensors(3, true);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.setArmingStatus(armingStatus);

//        verify(sensor).setActive(false);
        securityRepository.getSensors().forEach(sensor -> assertFalse(sensor.getActive()));
    }

    //Test 11: If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    public void processImage_armedHome_hasCat_returnAlarmOn() {
        BufferedImage image = new BufferedImage(1,1,1);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.processImage(image);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    public void changeSensorActivationStatus_deactivateSensor_setPendingAlarm() {
        sensor.setActive(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @Test
    public void addStatusListener_returnSizeOne() {
        securityService.addStatusListener(statusListener);

        assertTrue(securityService.hasStatusListener(statusListener));
    }

    @Test
    public void removeStatusListener_returnSizeZero() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);

        assertFalse(securityService.hasStatusListener(statusListener));
    }

    @Test
    public void addSensor_returnAddSensorCalled() {
        securityService.addSensor(sensor);

        verify(securityRepository).addSensor(sensor);
    }

    @Test
    public void removeSensor_returnRemoveSenSorCalled() {
        securityService.removeSensor(sensor);

        verify(securityRepository).removeSensor(sensor);
    }









}
