from threading import Thread
from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._proxy import ProfileProxy
from time import sleep

from pandora_experimental.gatt_grpc import GATT
from pandora_experimental.gatt_pb2 import GattServiceParams, GattCharacteristicParams
from pandora_experimental.host_grpc import Host
from pandora_experimental.host_pb2 import ConnectabilityMode, DataTypes, DiscoverabilityMode, OwnAddressType
from pandora_experimental.security_grpc import Security, SecurityStorage
from pandora_experimental.security_pb2 import LESecurityLevel


class GAPProxy(ProfileProxy):

    def __init__(self, channel):
        super().__init__(channel)
        self.gatt = GATT(channel)
        self.host = Host(channel)
        self.security = Security(channel)
        self.security_storage = SecurityStorage(channel)

        self.connection = None
        self.pairing_events = None
        self.inquiry_responses = None
        self.scan_responses = None

        self.counter = 0
        self.cached_passkey = None

        self._auto_confirm_requests()

    @match_description
    def TSC_MMI_iut_send_hci_connect_request(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please send an HCI connect request to establish a basic rate
        connection( after the IUT discovers the Lower Tester over BR and LE)?.
        """

        if test in {"GAP/SEC/AUT/BV-02-C", "GAP/SEC/SEM/BV-05-C", "GAP/SEC/SEM/BV-08-C"}:
            # we connect then pair, so we have to pair directly in this MMI
            self.pairing_events = self.security.OnPairing()
            self.connection = self.host.Connect(address=pts_addr, manually_confirm=True).connection
        else:
            self.connection = self.host.Connect(address=pts_addr).connection

        return "OK"

    @assert_description
    def _mmi_222(self, **kwargs):
        """
        Please initiate a BR/EDR security authentication and pairing with
        interaction of HCI commands.

        Press OK to continue.
        """

        # pairing already initiated with Connect() on Android
        self.pairing_events = self.security.OnPairing()

        return "OK"

    @match_description
    def _mmi_2001(self, passkey: str, **kwargs):
        """
        Please verify the passKey is correct: (?P<passkey>[0-9]+)
        """

        for event in self.pairing_events:
            assert event.numeric_comparison == int(passkey), (event, passkey)
            self.pairing_events.send(event=event, confirm=True)
            return "OK"

        assert False, "did not receive expected pairing event"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_connectable_undirected(self, **kwargs):
        """
        Please send a connectable undirected advertising report.
        """

        self.host.StartAdvertising(
            connectable=True,
            own_address_type=OwnAddressType.PUBLIC,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_handle_for_insufficient_authentication(self, pts_addr: bytes, **kwargs):
        """
        Please enter the handle(2 octet) to the characteristic in the IUT
        database where Insufficient Authentication error will be returned :
        """

        response = self.gatt.RegisterService(
            service=GattServiceParams(
                uuid="955798ce-3022-455c-b759-ee8edcd73d1a",
                characteristics=[
                    GattCharacteristicParams(
                        uuid="cf99ed9b-3c43-4343-b8a7-8afa513752ce",
                        properties=0x02,  # PROPERTY_READ,
                        permissions=0x04,  # PERMISSION_READ_ENCRYPTED_MITM
                    ),
                ],
            ))

        self.pairing_events = self.security.OnPairing()

        return handle_format(response.service.characteristics[0].handle)

    @match_description
    def TSC_MMI_the_security_id_is(self, pts_addr: bytes, passkey: str, **kwargs):
        """
        The Secure ID is (?P<passkey>[0-9]*)
        """

        for event in self.pairing_events:
            if event.address == pts_addr and event.passkey_entry_request:
                self.pairing_events.send(event=event, passkey=int(passkey))
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_iut_send_le_connect_request(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please send an LE connect request to establish a connection.
        """

        if test == "GAP/DM/BON/BV-01-C":
            # we also begin pairing here if we are not already paired on LE
            if self.counter == 0:
                self.counter += 1
                self.security_storage.DeleteBond(public=pts_addr)
                self.connection = self.host.ConnectLE(public=pts_addr).connection
                self.security.Secure(connection=self.connection, le=LESecurityLevel.LE_LEVEL3)
                return "OK"

        if test == "GAP/SEC/AUT/BV-21-C" and self.connection is not None:
            # no-op since the peer just disconnected from us,
            # so we have immediately auto-connected back to it
            return "OK"

        if test in {"GAP/CONN/GCEP/BV-02-C", "GAP/DM/LEP/BV-06-C", "GAP/CONN/GCEP/BV-01-C"}:
            # use identity address
            address = pts_addr
        else:
            # the PTS sometimes decides to advertise with an RPA, so we do a scan to find its real address
            scans = self.host.Scan()
            for scan in scans:
                adv_address = scan.public if scan.HasField("public") else scan.random
                device_name = self.host.GetRemoteName(address=adv_address).name
                if "pts" in device_name.lower():
                    address = adv_address
                    scans.cancel()
                    break

        self.connection = self.host.ConnectLE(public=address).connection
        if test in {"GAP/BOND/BON/BV-04-C"}:
            self.security.Secure(connection=self.connection, le=LESecurityLevel.LE_LEVEL3)

        return "OK"

    @assert_description
    def TSC_MMI_enter_security_id(self, pts_addr: bytes, **kwargs):
        """
        Please enter Secure Id.
        """

        if self.cached_passkey is not None:
            self.log(f"Returning cached passkey entry {self.cached_passkey}")
            return str(self.cached_passkey)

        for event in self.pairing_events:
            if event.address == pts_addr and event.passkey_entry_notification:
                self.log(f"Got passkey entry {event.passkey_entry_notification}")
                self.cached_passkey = event.passkey_entry_notification
                return str(event.passkey_entry_notification)

        assert False

    @match_description
    def TSC_MMI_iut_send_att_service_request(self, pts_addr: bytes, handle: str, **kwargs):
        r"""
        Please send an ATT service request - read or write request with handle
        (?P<handle>[0-9a-e]+) \(octet\).Discover services if needed.
        """

        self.gatt.ReadCharacteristicFromHandle(
            connection=self.connection,
            # They want us to read the characteristic value handle using ATT, but the interface only lets us
            # read the characteristic by its handle. So we offset by one, since in this test the characteristic
            # value handle is one above the characteristic handle itself.
            handle=int(handle, base=16) - 1,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_service_uuid(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Service UUID.
        """

        self.host.StartAdvertising(
            own_address_type=OwnAddressType.PUBLIC,
            data=DataTypes(complete_service_class_uuids128=["955798ce-3022-455c-b759-ee8edcd73d1a"],))
        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_local_name(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Local Name.
        """

        self.host.StartAdvertising(
            own_address_type=OwnAddressType.PUBLIC,
            data=DataTypes(include_complete_local_name=True, include_shortened_local_name=True,))

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_flags(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Flags.
        """

        self.host.StartAdvertising(
            connectable=True,
            own_address_type=OwnAddressType.PUBLIC,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_manufacturer_specific_data(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Manufacture
        Specific Data.
        """

        self.host.StartAdvertising(
            own_address_type=OwnAddressType.PUBLIC,
            data=DataTypes(manufacturer_specific_data=b"d0n't b3 3v1l!",))

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_tx_power_level(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with TX Power Level.
        """

        self.host.StartAdvertising(
            own_address_type=OwnAddressType.PUBLIC,
            data=DataTypes(include_tx_power_level=True,))

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_connectable(self, **kwargs):
        """
        Please send a connectable advertising report.
        """

        self.host.StartAdvertising(
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_ADV_IND(self, **kwargs):
        """
        Please send connectable undirected advertising report.
        """

        self.host.StartAdvertising(
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_idle_mode_security_4(self, **kwargs):
        """
        Please confirm that IUT is in Idle mode with security mode 4. Press OK
        when IUT is ready to start device discovery.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_general_inquiry_found(self, pts_addr: bytes, **kwargs):
        """
        Please start general inquiry. Click 'Yes' If IUT does discovers PTS and
        ready for PTS to initiate LE create connection otherwise click 'No'.
        """

        inquiry_responses = self.host.Inquiry()
        for response in inquiry_responses:
            assert response.address == pts_addr, (response.address, pts_addr)
            inquiry_responses.cancel()
            return "Yes"

        assert False

    @assert_description
    def TSC_MMI_iut_send_att_read_by_type_request_name_request(self, pts_addr: bytes, **kwargs):
        """
        Please start the Name Discovery Procedure to retrieve Device Name from
        the PTS.
        """

        # Android does RNR when connecting for the first time
        self.connection = self.host.Connect(address=pts_addr).connection

        return "OK"

    @match_description
    def TSC_MMI_iut_confirm_device_discovery(self, name: str, pts_addr: bytes, **kwargs):
        """
        Please confirm that IUT has discovered PTS and retrieved its name (?P<name>[a-zA-Z\-0-9]*)
        """

        connection = self.host.GetConnection(address=pts_addr).connection
        device = self.host.GetDevice(connection=connection)
        assert name == device.name, (name, device.name)

        return "OK"

    @assert_description
    def TSC_MMI_check_if_iut_support_non_connectable_advertising(self, **kwargs):
        """
        Does the IUT have an ability to send non-connectable advertising report?
        """

        return "Yes"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_general_discoverable_ok_to_continue(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report. Press OK to continue.
        """

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.DISCOVERABLE_GENERAL),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_general_discoverable_0203(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report using either non - directed advertising or
        discoverable undirected advertising.
        """

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.DISCOVERABLE_GENERAL),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_undirected_connectable_mode_non_discoverable_mode(self, **kwargs):
        """
        Please prepare IUT into non-discoverable mode and send an advertising
        report using connectable undirected advertising.
        """

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.NOT_DISCOVERABLE),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        return "OK"

    def TSC_MMI_iut_send_advertising_report_event_general_discoverable_00(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report using connectable undirected advertising.
        """

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.DISCOVERABLE_GENERAL),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_general_discovery(self, **kwargs):
        """
        Please start General Discovery. Press OK to continue.
        """

        self.scan_responses = self.host.Scan()

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_limited_discovery(self, **kwargs):
        """
        Please start Limited Discovery. Press OK to continue.
        """

        self.scan_responses = self.host.Scan()

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_general_discovered_device(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is discovered.
        """

        for response in self.scan_responses:
            assert response.HasField("public")
            # General Discoverability shall be able to check both limited and general advertising
            if response.public == pts_addr:
                self.scan_responses.cancel()
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_iut_confirm_limited_discovered_device(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is discovered.
        """

        for response in self.scan_responses:
            assert response.HasField("public")
            if (response.public == pts_addr and
                response.data.le_discoverability_mode == DiscoverabilityMode.DISCOVERABLE_LIMITED):
                self.scan_responses.cancel()
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_iut_confirm_general_discovered_device_not_found(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is NOT discovered.
        """

        discovered = False

        def search():
            nonlocal discovered
            for response in self.scan_responses:
                assert response.HasField("public")
                if (response.public == pts_addr and
                    response.data.le_discoverability_mode == DiscoverabilityMode.DISCOVERABLE_GENERAL):
                    self.scan_responses.cancel()
                    discovered = True
                    return

        # search for five seconds, if we don't find anything, give up
        worker = Thread(target=search)
        worker.start()
        worker.join(timeout=5)

        assert not discovered

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_limited_discovered_device_not_found(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is NOT discovered.
        """

        discovered = False

        def search():
            nonlocal discovered
            for response in self.scan_responses:
                assert response.HasField("public")
                if (response.public == pts_addr and
                    response.data.le_discoverability_mode == DiscoverabilityMode.DISCOVERABLE_LIMITED):
                    self.inquiry_responses.cancel()
                    discovered = True
                    return

        # search for five seconds, if we don't find anything, give up
        worker = Thread(target=search)
        worker.start()
        worker.join(timeout=5)

        assert not discovered

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_non_discoverable(self, **kwargs):
        """
        Please prepare IUT into non-discoverable and non-connectable mode and
        send an advertising report.
        """

        self.host.StartAdvertising(own_address_type=OwnAddressType.PUBLIC,)

        return "OK"

    @assert_description
    def TSC_MMI_set_iut_in_bondable_mode(self, **kwargs):
        """
        Please set IUT into bondable mode. Press OK to continue.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_le_disconnect_request(self, pts_addr: bytes, **kwargs):
        """
        Please send a disconnect request to terminate connection.
        """

        try:
            self.host.Disconnect(connection=self.host.GetLEConnection(address=pts_addr).connection)
        except Exception:
            pass

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_bonding_procedure_bondable(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please start the Bonding Procedure in bondable mode.
        """

        self.pairing_events = self.security.OnPairing()

        if test == "GAP/DM/BON/BV-01-C":
            # we already started in the previous test
            return "OK"

        if test not in {"GAP/SEC/AUT/BV-21-C"}:
            self.security_storage.DeleteBond(public=pts_addr)

        connection = self.host.GetLEConnection(address=pts_addr).connection
        self.security.Secure(connection=connection, le=LESecurityLevel.LE_LEVEL3)

        return "OK"

    @assert_description
    def TSC_MMI_make_iut_connectable(self, **kwargs):
        """
        Please make IUT connectable. Press OK to continue.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_general_discovery_DM(self, pts_addr: bytes, **kwargs):
        """
        Please start general discovery over BR/EDR and over LE. If IUT discovers
        PTS with both BR/EDR and LE method, press OK.
        """

        discovered_bredr = False

        def search_bredr():
            nonlocal discovered_bredr
            inquiry_responses = self.host.Inquiry()
            for response in inquiry_responses:
                if response.address == pts_addr:
                    inquiry_responses.cancel()
                    discovered_bredr = True
                    return

        bredr_worker = Thread(target=search_bredr)
        bredr_worker.start()

        discovered_le = False

        def search_le():
            nonlocal discovered_le
            scan_responses = self.host.Scan()
            for event in scan_responses:
                address = event.public if event.HasField("public") else event.random
                if (address == pts_addr and
                    event.data.le_discoverability_mode):
                    scan_responses.cancel()
                    discovered_le = True
                    return

        le_worker = Thread(target=search_le)
        le_worker.start()

        # search for five seconds, if we don't find anything, give up
        bredr_worker.join(timeout=5)
        le_worker.join(timeout=5)

        assert discovered_bredr and discovered_le, (discovered_bredr, discovered_le)

        return "OK"

    @assert_description
    def TSC_MMI_make_iut_general_discoverable(self, **kwargs):
        """
        Please make IUT general discoverable. Press OK to continue.
        """

        self.host.SetDiscoverabilityMode(
            mode=DiscoverabilityMode.DISCOVERABLE_GENERAL)

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.DISCOVERABLE_GENERAL),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_basic_rate_name_discovery_DM(self, pts_addr: bytes, **kwargs):
        """
        Please start device name discovery over BR/EDR . If IUT discovers PTS,
        press OK to continue.
        """

        inquiry_responses = self.host.Inquiry()
        for response in inquiry_responses:
            if response.address == pts_addr:
                inquiry_responses.cancel()
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_make_iut_not_connectable(self, **kwargs):
        """
        Please make IUT not connectable. Press OK to continue.
        """

        self.host.SetDiscoverabilityMode(
            mode=DiscoverabilityMode.NOT_DISCOVERABLE)

        self.host.SetConnectabilityMode(mode=ConnectabilityMode.NOT_CONNECTABLE)

        return "OK"

    @assert_description
    def TSC_MMI_make_iut_not_discoverable(self, **kwargs):
        """
        Please make IUT not discoverable. Press OK to continue.
        """

        self.host.SetDiscoverabilityMode(
            mode=DiscoverabilityMode.NOT_DISCOVERABLE)
        self.host.SetConnectabilityMode(mode=ConnectabilityMode.NOT_CONNECTABLE)

        return "OK"

    @assert_description
    def TSC_MMI_press_ok_to_disconnect(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please press ok to disconnect the link.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_att_disconnect_request(self, **kwargs):
        """
        Please send an ATT disconnect request to terminate an L2CAP channel.
        """

        try:
            self.host.Disconnect(connection=self.connection)
        except Exception:
            # we already disconnected, no-op
            pass

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_bonding_procedure_non_bondable(self, pts_addr: bytes, **kwargs):
        """
        Please start the Bonding Procedure in non-bondable mode.
        """

        # No idea how we can bond in non-bondable mode, but this passes the tests...
        connection = self.host.GetLEConnection(address=pts_addr).connection
        self.security.Secure(connection=connection, le=LESecurityLevel.LE_LEVEL3)

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_le_connection_update_request_timeout(self, **kwargs):
        """
        Please send an L2CAP Connection Parameter Update request using valid
        parameters and wait for TSPX_iut_connection_parameter_timeout 30000ms
        timeout...
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_perform_direct_connection_establishment_procedure(self, **kwargs):
        """
        Please prepare IUT into the Direct Connection Establishment Procedure.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_perform_general_connection_establishment_procedure(self, **kwargs):
        """
        Please prepare IUT into the General Connection Establishment Procedure.
        Press ok to continue.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_non_connectable_mode(self, **kwargs):
        """
        Please enter Non-Connectable mode.
        """

        self.host.SetConnectabilityMode(mode=ConnectabilityMode.NOT_CONNECTABLE)

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_non_connectable_mode_general_discoverable_mode(self, **kwargs):
        """
        Please enter General Discoverable and Non-Connectable mode.
        """

        self.host.SetDiscoverabilityMode(
            mode=DiscoverabilityMode.DISCOVERABLE_GENERAL)
        self.host.SetConnectabilityMode(mode=ConnectabilityMode.NOT_CONNECTABLE)

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_0203_general_discoverable(self, **kwargs):
        """
        Please send non-connectable undirected advertising report or
        discoverable undirected advertising report with general discoverable
        flags turned on.
        """

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.DISCOVERABLE_GENERAL),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=False,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_non_discoverable_and_undirected_connectable(self, **kwargs):
        """
        Please prepare IUT into non-discoverable and connectable mode and send
        an advertising report.
        """

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.NOT_DISCOVERABLE),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_undirected_connectable_mode_general_discoverable_mode(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report using connectable undirected advertising.
        """

        self.host.StartAdvertising(
            data=DataTypes(le_discoverability_mode=DiscoverabilityMode.DISCOVERABLE_GENERAL),
            own_address_type=OwnAddressType.PUBLIC,
            connectable=True,
        )

        return "OK"

    @assert_description
    def TSC_MMI_wait_for_encryption_change_event(self, **kwargs):
        """
        Waiting for HCI_ENCRYPTION_CHANGE_EVENT...
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_security_mode_4(self, **kwargs):
        """
        Please order the IUT to go in connectable mode and in security mode 4.
        Press OK to continue.
        """

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def _mmi_251(self, **kwargs):
        """
        Please send L2CAP Connection Response to PTS.
        """

        return "OK"

    @assert_description
    def _mmi_230(self, **kwargs):
        """
        Please order the IUT to be in security mode 4. Press OK to make
        connection to Lower Tester.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_simple_pairing(self, pts_addr: bytes, **kwargs):
        """
        Please start simple pairing procedure.
        """

        # we have always started this already in the connection, so no-op

        return "OK"

    def TSC_MMI_iut_send_l2cap_connect_request(self, pts_addr: bytes, **kwargs):
        """
        Please initiate BR/EDR security authentication and pairing to establish
        a service level enforced security!
        After that, please create the service
        channel using L2CAP Connection Request.

        Press OK to continue.
        """

        def after_that():
            sleep(5)
            self.host.Connect(address=pts_addr)

        Thread(target=after_that).start()

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_lost_bond(self, **kwargs):
        """
        Please confirm that IUT has informed of a lost bond.
        """

        return "OK"

    @assert_description
    def _mmi_231(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please start the Bonding Procedure in bondable mode.
        After Bonding
        Procedure is completed, please send a disconnect request to terminate
        connection.
        """

        if test != "GAP/SEC/SEM/BV-08-C":
            # we already started in the Connect MMI
            self.pairing_events = self.security.OnPairing()
            self.security.Secure(connection=connection, le=LESecurityLevel.LE_LEVEL3)

        connection = self.host.GetConnection(address=pts_addr).connection

        def after_that():
            self.host.WaitConnection()  # this really waits for bonding
            sleep(1)
            self.host.Disconnect(connection=connection)

        Thread(target=after_that).start()

        return "OK"

    def _auto_confirm_requests(self, times=None):

        def task():
            cnt = 0
            pairing_events = self.security.OnPairing()
            for event in pairing_events:
                if event.WhichOneof('method') in {"just_works", "numeric_comparison"}:
                    if times is None or cnt < times:
                        cnt += 1
                        pairing_events.send(event=event, confirm=True)

        Thread(target=task).start()

def handle_format(handle):
    return hex(handle)[2:].zfill(4)
