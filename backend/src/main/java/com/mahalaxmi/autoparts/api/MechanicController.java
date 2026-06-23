package com.mahalaxmi.autoparts.api;

import com.mahalaxmi.autoparts.domain.BillStatus;
import com.mahalaxmi.autoparts.domain.BillType;
import com.mahalaxmi.autoparts.domain.Mechanic;
import com.mahalaxmi.autoparts.repository.BillRepository;
import com.mahalaxmi.autoparts.repository.MechanicRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/mechanics")
public class MechanicController {
    private final MechanicRepository mechanics;
    private final BillRepository bills;

    public MechanicController(MechanicRepository mechanics, BillRepository bills) {
        this.mechanics = mechanics;
        this.bills = bills;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Dtos.MechanicResponse> mechanics() {
        return mechanics.findAllByOrderByMechanicNameAscGarageNameAsc().stream()
                .map(this::mechanicResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Dtos.MechanicDetailResponse mechanic(@PathVariable long id) {
        Mechanic mechanic = findMechanic(id);
        return new Dtos.MechanicDetailResponse(
                mechanicResponse(mechanic),
                bills.findByMechanicAndBillTypeAndStatusNotOrderByCreatedAtDesc(mechanic, BillType.ONGOING, BillStatus.CANCELLED)
                        .stream().map(ApiMapper::bill).toList(),
                bills.findByMechanicAndBillTypeAndStatusNotOrderByCreatedAtDesc(mechanic, BillType.FINAL, BillStatus.CANCELLED)
                        .stream().map(ApiMapper::bill).toList()
        );
    }

    @PostMapping
    @Transactional
    public Dtos.MechanicResponse createMechanic(@Valid @RequestBody Dtos.MechanicRequest request) {
        Mechanic mechanic = new Mechanic();
        apply(request, mechanic);
        return mechanicResponse(mechanics.save(mechanic));
    }

    @PutMapping("/{id}")
    @Transactional
    public Dtos.MechanicResponse updateMechanic(@PathVariable long id, @Valid @RequestBody Dtos.MechanicRequest request) {
        Mechanic mechanic = findMechanic(id);
        apply(request, mechanic);
        return mechanicResponse(mechanic);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteMechanic(@PathVariable long id) {
        Mechanic mechanic = findMechanic(id);
        long activeBills = bills.countByMechanicAndBillTypeAndStatusNot(mechanic, BillType.ONGOING, BillStatus.CANCELLED);
        if (activeBills > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot delete mechanic with active ongoing bills");
        }
        mechanics.delete(mechanic);
        return ResponseEntity.noContent().build();
    }

    private Dtos.MechanicResponse mechanicResponse(Mechanic mechanic) {
        return ApiMapper.mechanic(
                mechanic,
                bills.receivableFor(mechanic),
                bills.countByMechanicAndBillTypeAndStatusNot(mechanic, BillType.ONGOING, BillStatus.CANCELLED),
                bills.countByMechanicAndBillType(mechanic, BillType.FINAL)
        );
    }

    private Mechanic findMechanic(long id) {
        return mechanics.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mechanic not found"));
    }

    private void apply(Dtos.MechanicRequest request, Mechanic mechanic) {
        mechanic.setMechanicName(request.mechanicName().trim());
        mechanic.setGarageName(request.garageName().trim());
    }
}
